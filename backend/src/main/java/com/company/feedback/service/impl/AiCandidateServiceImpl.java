package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.feedback.common.JsonUtil;
import com.company.feedback.common.PageResult;
import com.company.feedback.common.ResultCode;
import com.company.feedback.common.StatusLabelUtil;
import com.company.feedback.common.UserContext;
import com.company.feedback.config.AiProperties;
import com.company.feedback.dto.*;
import com.company.feedback.entity.*;
import com.company.feedback.enums.*;
import com.company.feedback.exception.AiCallException;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.*;
import com.company.feedback.service.AiCandidateService;
import com.company.feedback.service.AiService;
import com.company.feedback.service.IssueService;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.AiCandidateVO;
import com.company.feedback.vo.AiLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 候选问题卡片服务实现
 *
 * <p><b>本类是 AI 模块的调度中枢，承担三件事</b>：
 * <ol>
 *   <li><b>选材</b>：挑出"待归类且尚未被任何候选卡片引用"的反馈；</li>
 *   <li><b>调度</b>：按配置选择大模型实现或规则实现，并在失败时自动降级；</li>
 *   <li><b>留证</b>：无论成功失败，都把输入材料、返回原文、耗时、结果写入日志表。</li>
 * </ol>
 *
 * <p><b>为什么注入的是两个具体实现类，而不是 AiService 集合</b>：
 * 降级必须明确"谁是首选、谁是兜底"。
 * 如果注入 {@code List<AiService>}，顺序依赖 Spring 的注入次序，
 * 一旦将来新增第三种实现，降级链路就变得不可预测。
 * 显式注入让降级路径在代码里一目了然。
 *
 * <p><b>为什么 generate 方法不加 @Transactional</b>：
 * 降级失败时要抛 503，但日志<b>必须</b>留下。
 * 若整个方法在一个事务里，抛异常会导致日志一并回滚，
 * 证据链就断了——这恰恰是本项目最不能接受的事。
 * 因此这里只在"写候选卡片"这一步用事务，日志单独提交。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCandidateServiceImpl implements AiCandidateService {

    /** 批次号格式：B + 年月日时分秒 */
    private static final DateTimeFormatter BATCH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 输入快照最大入库长度（防止几十条长反馈把日志表撑爆） */
    private static final int MAX_SNAPSHOT_LEN = 8000;

    /** 错误信息最大入库长度 */
    private static final int MAX_ERROR_LEN = 900;

    private final FeedbackMapper feedbackMapper;
    private final AiCandidateIssueMapper candidateMapper;
    private final AiGenerationLogMapper logMapper;
    private final SysUserMapper userMapper;
    private final IssueMapper issueMapper;
    private final FeedbackIssueLinkMapper feedbackIssueLinkMapper;

    private final IssueService issueService;
    private final AiProperties aiProperties;
    private final NotificationService notificationService;

    /** 首选实现：真实大模型 */
    private final LlmAiServiceImpl llmAiService;

    /** 兜底实现：本地规则 */
    private final RuleAiServiceImpl ruleAiService;

    // ==================================================================
    // 生成候选卡片
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>执行顺序：
     * <pre>
     *   ① 取待归类反馈（status=NEW 且未被候选卡片引用）
     *   ② 调用 AI 聚类（失败则按配置降级为规则实现）
     *   ③ 成功 → 写入 ai_candidate_issue（状态 DRAFT，纯草稿）
     *   ④ 无论成败 → 写入 ai_generation_log
     * </pre>
     */
    @Override
    public AiGenerateResult generate(AiGenerateRequest request) {

        // ① 选材
        String module = normalizeModule(request == null ? null : request.getModule());
        List<Feedback> pool = loadPendingPool(module);

        if (pool.isEmpty()) {
            return AiGenerateResult.builder()
                    .count(0)
                    .provider(AiProvider.RULE.getCode())
                    .fallback(false)
                    .message("当前没有待归类的反馈，请先由客服录入新反馈")
                    .candidates(Collections.emptyList())
                    .build();
        }

        String batchNo = generateBatchNo();
        String inputSnapshot = buildInputSnapshot(module, pool);

        // ② 调用 AI（内部含降级与日志）
        AiServiceResult<AiClusterResult> outcome = callWithFallback(
                AiBizType.CLUSTER, null, batchNo, inputSnapshot,
                svc -> {
                    AiClusterResult clusterResult = svc.cluster(pool);
                    return AiServiceResult.<AiClusterResult>builder()
                            .data(clusterResult)
                            .rawOutput(clusterResult == null
                                    ? null : clusterResult.getRawOutput())
                            .build();
                });

        AiClusterResult clusterResult = outcome == null ? null : outcome.getData();

        if (clusterResult == null || CollectionUtils.isEmpty(clusterResult.getCandidates())) {
            // 日志已在 callWithFallback 中落库（FAILED / TIMEOUT），这里只负责告知前端
            throw new BizException(ResultCode.AI_UNAVAILABLE,
                    "AI 服务暂不可用，已切换为手工模式。"
                            + "你可以跳过 AI，直接在「问题列表」中手工新建问题。");
        }

        // ③ 落库草稿
        List<AiCandidateIssue> saved =
                saveCandidates(batchNo, clusterResult.getCandidates(), module);

        // 通知所有 PM（排除触发人自己）：有候选卡片待确认
        notificationService.sendToRole(RoleEnum.PM.getCode(),
                NotificationType.AI_GENERATED,
                "AI 分析完成",
                "AI 聚类批次 " + batchNo + " 生成 " + saved.size()
                        + " 张候选卡片，待逐张确认",
                "/ai/candidates",
                UserContext.getUserId());

        boolean fallback =
                AiLogStatus.FALLBACK.getCode().equals(outcome.getStatus());

        return AiGenerateResult.builder()
                .batchNo(batchNo)
                .count(saved.size())
                .provider(outcome.getProvider())
                .fallback(fallback)
                .message(fallback
                        ? "AI 服务不可用，已自动降级为本地规则实现；以下结果由规则生成，请逐张确认。"
                        : "已生成候选卡片，均为草稿状态，需你逐张确认后才会成为正式问题。")
                .candidates(saved.stream().map(this::toVO).collect(Collectors.toList()))
                .build();
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** {@inheritDoc} */
    @Override
    public PageResult<AiCandidateVO> page(AiCandidateQueryRequest query) {

        long pageNo = (query.getPageNo() == null || query.getPageNo() < 1)
                ? 1L : query.getPageNo();
        long pageSize = (query.getPageSize() == null || query.getPageSize() < 1)
                ? 10L : query.getPageSize();

        LambdaQueryWrapper<AiCandidateIssue> wrapper =
                new LambdaQueryWrapper<AiCandidateIssue>()
                        .eq(StringUtils.hasText(query.getModule()),
                                AiCandidateIssue::getModule, query.getModule())
                        .eq(StringUtils.hasText(query.getStatus()),
                                AiCandidateIssue::getStatus, query.getStatus())
                        // 待处理的排在最前，已处理的按时间倒序
                        .orderByAsc(AiCandidateIssue::getStatus)
                        .orderByDesc(AiCandidateIssue::getCreateTime)
                        .orderByDesc(AiCandidateIssue::getId);

        IPage<AiCandidateIssue> pageResult =
                candidateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, String> userNames = resolveUserNames(
                pageResult.getRecords().stream()
                        .map(AiCandidateIssue::getHandledBy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()));

        return PageResult.of(pageResult, item -> toVO(item, userNames));
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<AiLogVO> logPage(AiLogQueryRequest query) {

        long pageNo = (query.getPageNo() == null || query.getPageNo() < 1)
                ? 1L : query.getPageNo();
        long pageSize = (query.getPageSize() == null || query.getPageSize() < 1)
                ? 10L : query.getPageSize();

        LambdaQueryWrapper<AiGenerationLog> wrapper =
                new LambdaQueryWrapper<AiGenerationLog>()
                        .eq(StringUtils.hasText(query.getBizType()),
                                AiGenerationLog::getBizType, query.getBizType())
                        .eq(StringUtils.hasText(query.getStatus()),
                                AiGenerationLog::getStatus, query.getStatus())
                        .orderByDesc(AiGenerationLog::getCreateTime)
                        .orderByDesc(AiGenerationLog::getId);

        IPage<AiGenerationLog> pageResult =
                logMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Map<Long, SysUser> users = resolveUsers(
                pageResult.getRecords().stream()
                        .map(AiGenerationLog::getOperatorId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()));

        return PageResult.of(pageResult, item -> toVO(item, users));
    }

    /** {@inheritDoc} */
    @Override
    public AiLogVO logDetail(Long id) {

        AiGenerationLog entity = logMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "AI 调用记录不存在");
        }
        Map<Long, SysUser> users = entity.getOperatorId() == null
                ? Collections.emptyMap()
                : resolveUsers(List.of(entity.getOperatorId()));

        return toVO(entity, users);
    }

    // ==================================================================
    // 确认 / 驳回
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>本方法只做参数透传，真正的落库与状态回写在
     * {@link IssueService#confirmFromCandidate} 中完成（带事务与乐观锁）。
     */
    @Override
    public Map<String, Long> confirm(Long id, AiCandidateConfirmRequest request) {

        Issue issue = issueService.confirmFromCandidate(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getFinalContent(),
                request.getAcceptanceCriteria());

        return Map.of("issueId", issue.getId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>驳回后卡片状态变为 REJECTED，
     * 其关联反馈若没有挂在其它问题上，则回到"未归类"，
     * 下一轮生成时会重新参与归纳，<b>数据不会丢失</b>。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, AiCandidateRejectRequest request) {

        AiCandidateIssue candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，不能重复驳回");
        }

        AiCandidateIssue update = new AiCandidateIssue();
        update.setId(id);
        update.setStatus(CandidateStatus.REJECTED.getCode());
        update.setHandleRemark(request.getReason().trim());
        update.setHandledBy(UserContext.getUserId());
        update.setHandleTime(LocalDateTime.now());
        // 乐观锁：并发驳回时后者失败
        update.setVersion(candidate.getVersion());

        if (candidateMapper.updateById(update) == 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已被他人处理，请刷新后重试");
        }

        // 关联反馈回到未归类，并通知录入人
        for (Long feedbackId :
                JsonUtil.jsonToLongList(candidate.getRelatedFeedbackIds())) {
            resetFeedbackIfNoIssue(feedbackId, request.getReason().trim());
        }

        log.info("候选卡片已驳回, candidateId={}, operator={}",
                id, UserContext.getUserIdOrNull());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>拆分与驳回的本质区别</b>：驳回是否定 AI 的全部建议；
     * 拆分是否定"这几件事是一件事"这个归并决策，
     * 问题本身仍然成立，只是要分开处理。
     * 因此拆分会生成新的 DRAFT 卡片继续走确认流程，
     * 而不是把反馈丢回未归类池。
     *
     * <p><b>允许同一条反馈出现在多张新卡片上</b>：
     * 混卡的根源往往就是一条反馈里写了多件事，
     * 按反馈硬拆根本拆不开。这里允许 PM 为每个部分改写
     * 标题/现象/诉求并重复引用同一条反馈；
     * 反馈与问题本来就是多对多（feedback_issue_link），数据层无障碍。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> split(Long id, AiCandidateSplitRequest request) {

        AiCandidateIssue candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，不能拆分");
        }

        List<AiCandidateSplitRequest.Part> parts = request.getParts();
        if (parts == null || parts.size() < 2) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "拆分至少要分成 2 张候选卡片");
        }

        List<Long> originalIds = JsonUtil.jsonToLongList(
                candidate.getRelatedFeedbackIds());

        // ① 校验反馈覆盖：每个部分的引用必须是原集合的子集，
        //    且并集必须覆盖原集合——否则会有反馈在拆分后掉出归类流程
        Set<Long> covered = new HashSet<>();
        for (AiCandidateSplitRequest.Part part : parts) {
            List<Long> partIds = CollectionUtils.isEmpty(part.getFeedbackIds())
                    ? originalIds : part.getFeedbackIds();
            for (Long feedbackId : partIds) {
                if (!originalIds.contains(feedbackId)) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "反馈 #" + feedbackId + " 不属于该候选卡片，无法归入拆分部分");
                }
                covered.add(feedbackId);
            }
        }
        for (Long originalId : originalIds) {
            if (!covered.contains(originalId)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "反馈 #" + originalId + " 没有被任何拆分部分接收，"
                                + "请为它指定去向（同一条反馈可以同时归入多个部分）");
            }
        }

        // ② 生成 N 张新 DRAFT 卡片
        //
        // aiConfidence 置空：置信度表达的是"AI 对这次归纳的确信程度"，
        // 拆分后的内容是 PM 的人工归纳，套上 AI 的分数会误导后续判断。
        String batchNo = candidate.getBatchNo();
        List<Long> createdIds = new ArrayList<>();

        for (AiCandidateSplitRequest.Part part : parts) {
            List<Long> partIds = CollectionUtils.isEmpty(part.getFeedbackIds())
                    ? originalIds : part.getFeedbackIds().stream()
                            .sorted()
                            .collect(Collectors.toList());

            AiCandidateIssue entity = new AiCandidateIssue();
            entity.setBatchNo(batchNo);
            entity.setTitle(part.getTitle().trim());
            entity.setPhenomenon(trimToNull(part.getPhenomenon()));
            entity.setUserDemand(trimToNull(part.getUserDemand()));
            entity.setModule(candidate.getModule());
            entity.setAiConfidence(null);
            entity.setAiReason("由 PM 手动拆分候选卡片 #" + id
                    + " 生成（原卡片混合了多个独立问题）。本卡片内容为人工归纳，非 AI 产出。");
            entity.setRelatedFeedbackIds(JsonUtil.toJson(partIds));
            entity.setStatus(CandidateStatus.DRAFT.getCode());
            entity.setVersion(0);

            candidateMapper.insert(entity);
            createdIds.add(entity.getId());
        }

        // ③ 原卡片转 SPLITTED 留档
        //
        // AI 原稿仍在（title/phenomenon/ai_reason 原样保留），
        // handle_remark 记录去向，构成完整追溯链。
        String remark = "已手动拆分为 " + parts.size() + " 张候选卡片："
                + createdIds.stream()
                        .map(newId -> "#" + newId)
                        .collect(Collectors.joining("、"));

        AiCandidateIssue update = new AiCandidateIssue();
        update.setId(id);
        update.setStatus(CandidateStatus.SPLITTED.getCode());
        update.setHandleRemark(remark);
        update.setHandledBy(UserContext.getUserId());
        update.setHandleTime(LocalDateTime.now());
        update.setVersion(candidate.getVersion());

        if (candidateMapper.updateById(update) == 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已被他人处理，请刷新后重试");
        }

        log.info("候选卡片已拆分, candidateId={}, parts={}, newIds={}, operator={}",
                id, parts.size(), createdIds, UserContext.getUserIdOrNull());

        return createdIds;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>为什么直接复用 cluster 而不新写提示词</b>：
     * "这张卡片混了几个问题"和"这批反馈该归成几张卡片"是同一个问题，
     * 只是输入范围从"整批待归类反馈"缩小到"这张卡片关联的反馈"。
     * 因此这里走同一条 {@link AiService#cluster} 链路，
     * 提示词里"一条反馈写了多件事时允许 id 分别写进多张卡片"的约束
     * 天然覆盖了拆分场景，不需要维护第二套提示词。
     *
     * <p><b>本方法只读</b>：除写 AI 调用日志外不产生任何副作用，
     * PM 关掉弹窗就等于什么都没发生。
     */
    @Override
    public AiCandidateSplitSuggestion suggestSplit(Long id) {

        AiCandidateIssue candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，无需再拆分");
        }

        List<Long> feedbackIds = JsonUtil.jsonToLongList(candidate.getRelatedFeedbackIds());
        if (CollectionUtils.isEmpty(feedbackIds)) {
            return AiCandidateSplitSuggestion.builder()
                    .available(false)
                    .splitSuggested(false)
                    .message("这张卡片没有关联反馈，AI 无从判断拆分点，请手动填写。")
                    .parts(Collections.emptyList())
                    .build();
        }

        // 按 ID 升序取，保证同一张卡片每次识别的输入顺序一致（结果可复现）
        List<Feedback> feedbacks = feedbackMapper.selectBatchIds(feedbackIds).stream()
                .sorted(Comparator.comparing(Feedback::getId))
                .collect(Collectors.toList());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("candidateId", id);
        input.put("candidateTitle", candidate.getTitle());
        input.put("module", candidate.getModule());
        input.put("feedbacks", feedbacks.stream()
                .map(f -> Map.of("id", f.getId(), "content", f.getContent()))
                .collect(Collectors.toList()));
        String inputSnapshot = truncate(input, MAX_SNAPSHOT_LEN);

        AiServiceResult<AiClusterResult> outcome = callWithFallback(
                AiBizType.CLUSTER, id, null, inputSnapshot,
                svc -> {
                    AiClusterResult clusterResult = svc.cluster(feedbacks);
                    return AiServiceResult.<AiClusterResult>builder()
                            .data(clusterResult)
                            .rawOutput(clusterResult == null
                                    ? null : clusterResult.getRawOutput())
                            .build();
                });

        if (outcome == null || outcome.getData() == null) {
            return AiCandidateSplitSuggestion.builder()
                    .available(false)
                    .splitSuggested(false)
                    .message("AI 暂不可用，无法给出拆分建议，请手动填写各部分内容。")
                    .parts(Collections.emptyList())
                    .build();
        }

        List<AiCandidateSuggestion> suggestions = outcome.getData().getCandidates();
        boolean fallback = AiLogStatus.FALLBACK.getCode().equals(outcome.getStatus());

        if (CollectionUtils.isEmpty(suggestions)) {
            return AiCandidateSplitSuggestion.builder()
                    .available(true)
                    .splitSuggested(false)
                    .provider(outcome.getProvider())
                    .fallback(fallback)
                    .message("AI 没能从这些反馈里归纳出问题，请手动填写。")
                    .parts(Collections.emptyList())
                    .build();
        }

        List<AiCandidateSplitRequest.Part> parts = suggestions.stream()
                .map(s -> {
                    AiCandidateSplitRequest.Part part = new AiCandidateSplitRequest.Part();
                    part.setTitle(s.getTitle());
                    part.setPhenomenon(s.getPhenomenon());
                    part.setUserDemand(s.getUserDemand());

                    // 只保留属于原卡片的反馈：AI 可能编出不存在的 ID，
                    // 而那会让 PM 一提交就被后端的"子集校验"挡回来
                    List<Long> partIds = CollectionUtils.isEmpty(s.getFeedbackIds())
                            ? Collections.emptyList()
                            : s.getFeedbackIds().stream()
                                    .filter(feedbackIds::contains)
                                    .distinct()
                                    .collect(Collectors.toList());

                    // 归属缺失时兜底为原卡片的全部反馈：
                    // 拆分校验要求每个部分至少挂一条反馈，空列表会被判为"未指定"
                    part.setFeedbackIds(partIds.isEmpty()
                            ? new ArrayList<>(feedbackIds) : partIds);
                    return part;
                })
                .collect(Collectors.toList());

        boolean suggested = parts.size() >= 2;
        String message = suggested
                ? "AI 识别出 " + parts.size() + " 个独立问题，已按问题分别预填，可直接修改后提交。"
                : "AI 判断这些反馈描述的是同一个问题，没有识别出可拆分点；"
                + "你仍可以手动添加部分强制拆分。";

        log.info("拆分建议已生成, candidateId={}, parts={}, provider={}, fallback={}",
                id, parts.size(), outcome.getProvider(), fallback);

        return AiCandidateSplitSuggestion.builder()
                .available(true)
                .splitSuggested(suggested)
                .provider(outcome.getProvider())
                .fallback(fallback)
                .message(message)
                .parts(parts)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>合并与拆分互补</b>：拆分处理"一张卡片混了多个问题"，
     * 合并处理"这张卡片描述的问题已经存在"。
     * 合并不会新建问题，而是复用手工关联的幂等逻辑
     * （{@link IssueService#linkFeedbacks}）把反馈挂到目标问题上；
     * 原卡片转 MERGED 留档，AI 原稿仍可追溯。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long merge(Long id, AiCandidateMergeRequest request) {

        AiCandidateIssue candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，不能合并");
        }

        Long targetIssueId = request.getTargetIssueId();
        Issue target = issueMapper.selectById(targetIssueId);
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "目标问题不存在或已被删除");
        }

        List<Long> feedbackIds =
                JsonUtil.jsonToLongList(candidate.getRelatedFeedbackIds());
        if (CollectionUtils.isEmpty(feedbackIds)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该候选卡片没有关联反馈，无法合并");
        }

        // ① 关联反馈挂到目标问题上（幂等，已存在自动跳过；
        //    反馈状态随之推进为 LINKED）
        LinkFeedbackRequest linkRequest = new LinkFeedbackRequest();
        linkRequest.setFeedbackIds(feedbackIds);
        linkRequest.setRemark(StringUtils.hasText(request.getRemark())
                ? request.getRemark().trim()
                : "由候选卡片 #" + id + "（" + candidate.getTitle() + "）合并而来");
        issueService.linkFeedbacks(targetIssueId, linkRequest);

        // ② 原卡片转 MERGED 留档
        //
        // MERGED 会继续参与"占用统计"（loadReferencedFeedbackIds），
        // 这些反馈不会重新进入待归类池，避免下一轮又被聚成新卡片。
        String remark = "已并入问题 #" + targetIssueId
                + "「" + target.getTitle() + "」"
                + (StringUtils.hasText(request.getRemark())
                        ? "；" + request.getRemark().trim() : "");

        AiCandidateIssue update = new AiCandidateIssue();
        update.setId(id);
        update.setStatus(CandidateStatus.MERGED.getCode());
        update.setHandleRemark(remark);
        update.setHandledBy(UserContext.getUserId());
        update.setHandleTime(LocalDateTime.now());
        update.setVersion(candidate.getVersion());

        if (candidateMapper.updateById(update) == 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已被他人处理，请刷新后重试");
        }

        log.info("候选卡片已合并, candidateId={}, targetIssueId={}, feedbackIds={}, operator={}",
                id, targetIssueId, feedbackIds, UserContext.getUserIdOrNull());

        return targetIssueId;
    }

    // ==================================================================
    // AI 作用点 C：起草需求
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p><b>本方法只读不写</b>（除了写日志）：
     * 它不会修改 issue 的任何业务字段，只返回草稿文本。
     */
    @Override
    public AiDraftResult draftForIssue(Long issueId) {

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在");
        }

        List<String> contents = loadLinkedFeedbackContents(issueId);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("issueId", issueId);
        input.put("issueNo", issue.getIssueNo());
        input.put("title", issue.getTitle());
        input.put("module", issue.getModule());
        input.put("description", issue.getDescription());
        input.put("feedbackContents", contents);
        String inputSnapshot = truncate(input, MAX_SNAPSHOT_LEN);

        AiServiceResult<AiDraftResult> outcome = callWithFallback(
                AiBizType.DRAFT, issueId, null, inputSnapshot,
                svc -> {
                    AiDraftResult draft = svc.draft(issue, contents);
                    return AiServiceResult.<AiDraftResult>builder()
                            .data(draft)
                            .rawOutput(draft == null ? null : draft.getRawOutput())
                            .build();
                });

        if (outcome == null || outcome.getData() == null) {
            throw new BizException(ResultCode.AI_UNAVAILABLE,
                    "AI 服务暂不可用，无法起草需求，请手工填写需求说明与验收条件。");
        }

        return outcome.getData();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>本方法只读不写</b>（除了写日志）：
     * 候选卡片尚未成为正式问题，这里用卡片自身信息构造一个
     * 临时 Issue 对象传给 DRAFT 作用点，不会触碰 issue 表。
     */
    @Override
    public AiDraftSuggestion suggestDraft(Long id) {

        AiCandidateIssue candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，无需再预填");
        }

        List<Long> feedbackIds = JsonUtil.jsonToLongList(candidate.getRelatedFeedbackIds());
        if (CollectionUtils.isEmpty(feedbackIds)) {
            return AiDraftSuggestion.builder()
                    .available(false)
                    .message("这张卡片没有关联反馈，AI 无从起草，请手动填写验收条件。")
                    .build();
        }

        // 按 ID 升序取，保证同一张卡片每次起草的输入顺序一致（结果可复现）
        List<String> contents = feedbackMapper.selectBatchIds(feedbackIds).stream()
                .sorted(Comparator.comparing(Feedback::getId))
                .map(Feedback::getContent)
                .collect(Collectors.toList());

        // 临时 Issue：DRAFT 作用点只读 title/module/description 与反馈原文，
        // 用卡片字段填充即可，不需要（也不应该）真正落一条 issue 记录
        Issue pseudoIssue = new Issue();
        pseudoIssue.setTitle(candidate.getTitle());
        pseudoIssue.setModule(candidate.getModule());
        List<String> descParts = new ArrayList<>();
        if (StringUtils.hasText(candidate.getPhenomenon())) {
            descParts.add("【现象】" + candidate.getPhenomenon());
        }
        if (StringUtils.hasText(candidate.getUserDemand())) {
            descParts.add("【诉求】" + candidate.getUserDemand());
        }
        pseudoIssue.setDescription(String.join("\n\n", descParts));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("candidateId", id);
        input.put("candidateTitle", candidate.getTitle());
        input.put("module", candidate.getModule());
        input.put("feedbackContents", contents);
        String inputSnapshot = truncate(input, MAX_SNAPSHOT_LEN);

        AiServiceResult<AiDraftResult> outcome = callWithFallback(
                AiBizType.DRAFT, id, null, inputSnapshot,
                svc -> {
                    AiDraftResult draft = svc.draft(pseudoIssue, contents);
                    return AiServiceResult.<AiDraftResult>builder()
                            .data(draft)
                            .rawOutput(draft == null ? null : draft.getRawOutput())
                            .build();
                });

        if (outcome == null || outcome.getData() == null) {
            return AiDraftSuggestion.builder()
                    .available(false)
                    .message("AI 暂不可用，无法生成起草建议，请手动填写验收条件。")
                    .build();
        }

        AiDraftResult draft = outcome.getData();
        boolean fallback = AiLogStatus.FALLBACK.getCode().equals(outcome.getStatus());

        String criteria = draft.getAcceptanceCriteria();
        String message = StringUtils.hasText(criteria)
                ? "已根据关联反馈生成验收条件建议，可直接修改后随确认提交。"
                : "AI 未给出验收条件建议，请手动填写。";

        log.info("候选卡片起草建议已生成, candidateId={}, hasCriteria={}, provider={}, fallback={}",
                id, StringUtils.hasText(criteria), outcome.getProvider(), fallback);

        return AiDraftSuggestion.builder()
                .available(true)
                .message(message)
                .draftContent(draft.getDraftContent())
                .acceptanceCriteria(criteria)
                .provider(outcome.getProvider())
                .fallback(fallback)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> status() {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expectedProvider", aiProperties.getProvider());
        map.put("llmConfigured", aiProperties.isLlmAvailable());

        AiProperties.Llm llm = aiProperties.getLlm();
        map.put("llmBaseUrl", llm == null ? null : llm.getBaseUrl());
        map.put("llmModel", llm == null ? null : llm.getModel());
        map.put("llmTimeoutMs", llm == null ? null : llm.getTimeoutMs());

        map.put("fallbackEnabled", aiProperties.getFallback().isEnabled());
        map.put("simulateFailure", aiProperties.getFallback().isSimulateFailure());

        AiService actual = chooseService();
        map.put("actualProvider", actual.provider());
        map.put("actualModel", actual.model());

        return map;
    }

    // ==================================================================
    // 降级调度（AI 模块的核心机制）
    // ==================================================================

    /**
     * 统一调度 AI 调用：计时 → 失败捕获 → 降级 → 落日志
     *
     * <p><b>降级判定条件</b>（两条同时满足才降级）：
     * <ol>
     *   <li>配置里 {@code ai.fallback.enabled=true}</li>
     *   <li>当前用的不是规则实现（否则没得降）</li>
     * </ol>
     *
     * <p><b>日志状态判定</b>：
     * <ul>
     *   <li>首选实现直接成功 → SUCCESS</li>
     *   <li>首选失败、规则兜底成功 → FALLBACK（error_msg 记录首选的失败原因）</li>
     *   <li>规则兜底也失败，或未启用降级 → FAILED / TIMEOUT</li>
     * </ul>
     *
     * @param bizType       业务类型
     * @param bizId         关联业务 ID
     * @param batchNo       批次号
     * @param inputSnapshot 输入材料快照
     * @param caller        真正的 AI 调用逻辑
     * @param <T>           返回数据类型
     * @return 调用结果（失败时为 null）
     */
    private <T> AiServiceResult<T> callWithFallback(
            AiBizType bizType,
            Long bizId,
            String batchNo,
            String inputSnapshot,
            Function<AiService, AiServiceResult<T>> caller) {

        AiService service = chooseService();
        long start = System.currentTimeMillis();

        AiServiceResult<T> result = null;
        AiLogStatus status = AiLogStatus.SUCCESS;
        String errorMsg = null;

        try {
            result = caller.apply(service);
            if (result == null || result.getData() == null) {
                throw new AiCallException(AiLogStatus.FAILED, "AI 返回结果为空");
            }
        } catch (AiCallException e) {
            status = e.getLogStatus();
            errorMsg = truncate(e.getMessage(), MAX_ERROR_LEN);
            log.warn("AI调用失败, bizType={}, provider={}, reason={}",
                    bizType.getCode(), service.provider(), errorMsg);

            boolean canFallback = aiProperties.getFallback().isEnabled()
                    && service != ruleAiService;

            if (canFallback) {
                try {
                    result = caller.apply(ruleAiService);
                    if (result == null || result.getData() == null) {
                        throw new AiCallException(AiLogStatus.FAILED,
                                "规则兜底返回结果为空");
                    }
                    service = ruleAiService;
                    status = AiLogStatus.FALLBACK;
                    log.warn("已降级为本地规则实现, bizType={}", bizType.getCode());
                } catch (AiCallException ex) {
                    status = AiLogStatus.FAILED;
                    errorMsg = truncate(ex.getMessage(), MAX_ERROR_LEN);
                }
            }
        }

        long cost = System.currentTimeMillis() - start;
        int costMs = (int) Math.min(cost, Integer.MAX_VALUE);

        if (result != null) {
            // 把"实际使用了谁、结果如何、耗时多久"回填进结果对象，
            // 让调用方一次拿到全部信息，不必再去翻日志表
            result.setProvider(service.provider());
            result.setModel(service.model());
            result.setStatus(status.getCode());
            result.setCostMs(costMs);
        }

        // 无论成功失败，一律落库——失败日志同样是证据
        saveLog(bizType, bizId, batchNo, inputSnapshot,
                result, status, errorMsg, costMs, service);

        return result;
    }

    /**
     * 选择首选实现
     *
     * <p>注意：即使没配 Key，只要 {@code ai.provider=llm} 也交给大模型实现去处理，
     * 由它在内部快速抛出"未配置 Key"的确定性失败，
     * 再由这里统一降级。这样"首选/兜底"的关系始终清晰。
     */
    private AiService chooseService() {
        boolean wantLlm = "llm".equalsIgnoreCase(aiProperties.getProvider())
                || aiProperties.getFallback().isSimulateFailure();
        return wantLlm ? llmAiService : ruleAiService;
    }

    /**
     * 写入 AI 调用日志
     *
     * <p>本方法是追加写，不提供任何更新入口。
     */
    private void saveLog(AiBizType bizType, Long bizId, String batchNo,
                         String inputSnapshot, AiServiceResult<?> result,
                         AiLogStatus status, String errorMsg, int costMs,
                         AiService service) {

        AiGenerationLog entity = new AiGenerationLog();
        entity.setBizType(bizType.getCode());
        entity.setBizId(bizId);
        entity.setBatchNo(batchNo);
        entity.setProvider(service.provider());
        entity.setModel(service.model());
        entity.setInputSnapshot(inputSnapshot);
        entity.setOutputRaw(result == null ? null : result.getRawOutput());
        entity.setStatus(status.getCode());
        entity.setErrorMsg(errorMsg);
        entity.setCostMs(costMs);
        entity.setOperatorId(UserContext.getUserIdOrNull());

        logMapper.insert(entity);
    }

    // ==================================================================
    // 选材：待归类且未被占用的反馈
    // ==================================================================

    /**
     * 载入待归类反馈池
     *
     * <p>条件：
     * <ul>
     *   <li>feedback.status = NEW</li>
     *   <li>若指定了模块，则 module 必须匹配</li>
     *   <li><b>排除</b>已被引用的反馈：只要它出现在某张未驳回候选卡片的
     *       related_feedback_ids 里，就不再重复进入下一批，
     *       避免同一条反馈被反复生成卡片</li>
     * </ul>
     *
     * @param module 模块代码，null 表示不限
     * @return 待归类反馈列表（按 ID 升序，保证结果稳定可复现）
     */
    private List<Feedback> loadPendingPool(String module) {

        Set<Long> referenced = loadReferencedFeedbackIds();

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getStatus, FeedbackStatus.NEW.getCode())
                .orderByAsc(Feedback::getId);

        if (module != null) {
            wrapper.eq(Feedback::getModule, module);
        }

        return feedbackMapper.selectList(wrapper).stream()
                .filter(item -> !referenced.contains(item.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 收集所有"仍被占用"的反馈 ID
     *
     * <p>只统计 DRAFT / CONFIRMED / MODIFIED / MERGED 四种状态的卡片：
     * 被驳回的卡片已经释放了它的反馈，那些反馈应当重新参与归纳。
     *
     * @return 被引用的反馈 ID 集合
     */
    private Set<Long> loadReferencedFeedbackIds() {

        List<String> occupiedStatuses = List.of(
                CandidateStatus.DRAFT.getCode(),
                CandidateStatus.CONFIRMED.getCode(),
                CandidateStatus.MODIFIED.getCode(),
                CandidateStatus.MERGED.getCode());

        List<AiCandidateIssue> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<AiCandidateIssue>()
                        .select(AiCandidateIssue::getRelatedFeedbackIds)
                        .in(AiCandidateIssue::getStatus, occupiedStatuses));

        Set<Long> ids = new HashSet<>();
        for (AiCandidateIssue candidate : candidates) {
            ids.addAll(JsonUtil.jsonToLongList(candidate.getRelatedFeedbackIds()));
        }
        return ids;
    }

    /**
     * 构造输入材料快照
     *
     * <p>把本次喂给 AI 的每一条反馈的 id、模块、原文都记录下来。
     * 没有它，事后就无法回答"AI 当时究竟看到了什么"。
     *
     * @param module 模块代码，可为 null
     * @param pool   反馈池
     * @return JSON 字符串
     */
    private String buildInputSnapshot(String module, List<Feedback> pool) {

        List<Map<String, Object>> items = new ArrayList<>();
        for (Feedback fb : pool) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fb.getId());
            item.put("feedbackNo", fb.getFeedbackNo());
            item.put("module", fb.getModule());
            item.put("content", fb.getContent());
            items.add(item);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("module", module == null ? "ALL" : module);
        snapshot.put("feedbackCount", pool.size());
        snapshot.put("feedbacks", items);

        return truncate(snapshot, MAX_SNAPSHOT_LEN);
    }

    // ==================================================================
    // 落库候选卡片
    // ==================================================================

    /**
     * 把 AI 建议落库为草稿卡片
     *
     * <p>这里写入的是 ai_candidate_issue（草稿），
     * <b>不会</b>触碰 issue / feedback / improvement_task 任何一张正式业务表。
     * 这就是"AI 产出与人工决策物理分离"在数据层的体现。
     *
     * <p><b>为什么这里不套事务</b>：外层 {@link #generate} 刻意不使用事务，
     * 以保证 AI 失败时日志仍能独立提交。因此这里逐条插入、各自提交，
     * 不声明事务注解（声明了也不会生效，属同类自调用）。
     *
     * @param batchNo     批次号
     * @param suggestions AI 建议列表
     * @param module      模块代码（AI 未给出模块时的兜底值）
     * @return 已保存的卡片列表
     */
    public List<AiCandidateIssue> saveCandidates(
            String batchNo,
            List<AiCandidateSuggestion> suggestions,
            String module) {

        List<AiCandidateIssue> saved = new ArrayList<>();

        for (AiCandidateSuggestion suggestion : suggestions) {

            AiCandidateIssue entity = new AiCandidateIssue();
            entity.setBatchNo(batchNo);
            entity.setTitle(StringUtils.hasText(suggestion.getTitle())
                    ? suggestion.getTitle().trim() : "（AI 未给出标题）");
            entity.setPhenomenon(suggestion.getPhenomenon());
            entity.setUserDemand(suggestion.getUserDemand());
            entity.setModule(StringUtils.hasText(suggestion.getModule())
                    ? suggestion.getModule() : module);
            entity.setAiConfidence(suggestion.getConfidence() == null
                    ? new BigDecimal("0.50") : suggestion.getConfidence());
            entity.setAiReason(suggestion.getReason());
            entity.setRelatedFeedbackIds(
                    JsonUtil.toJson(suggestion.getFeedbackIds()));
            // 草稿：AI 生成后等待 PM 处理，不产生任何业务事实
            entity.setStatus(CandidateStatus.DRAFT.getCode());
            entity.setVersion(0);

            candidateMapper.insert(entity);
            saved.add(entity);
        }

        return saved;
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /**
     * 规范化模块参数
     *
     * @param module 入参模块代码，允许为空
     * @return 合法返回大写代码；为空返回 null（表示不限模块）
     */
    private String normalizeModule(String module) {
        if (!StringUtils.hasText(module)) {
            return null;
        }
        String trimmed = module.trim();
        if (!FeedbackModule.isValid(trimmed)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "无效的功能模块：" + trimmed);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 生成批次号
     */
    private String generateBatchNo() {
        return "B" + LocalDateTime.now().format(BATCH_FORMATTER);
    }

    /**
     * 驳回后把反馈状态复位
     *
     * <p>只有当这条反馈<b>没有挂在任何问题上</b>时才复位为 NEW，
     * 避免"还挂在别的问题上却显示未归类"的自相矛盾。
     *
     * <p>同时通知录入人「候选被驳回」：客服上传反馈后最关心的就是
     * "我报的问题公司怎么处理的"，候选被否决也是一种处理结果，
     * 不通知会让客服一直悬着。文案必须如实说明"回到待归类池、
     * 会重新参与归纳"——这不是最终否决，避免客服误解为反馈被毙掉。
     *
     * @param feedbackId   反馈 ID
     * @param rejectReason PM 填写的驳回原因（可为空）
     */
    private void resetFeedbackIfNoIssue(Long feedbackId, String rejectReason) {

        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            return;
        }

        long linkCount = feedbackIssueLinkMapper.selectCount(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getFeedbackId, feedbackId));

        if (linkCount > 0) {
            // 还挂在其它问题上，说明该反馈已有归宿，不复位也不打扰
            return;
        }

        Feedback update = new Feedback();
        update.setId(feedbackId);
        update.setStatus(FeedbackStatus.NEW.getCode());
        update.setVersion(feedback.getVersion());
        feedbackMapper.updateById(update);

        // 通知录入人（操作人是录入人本人时不发，如 PM 自己录入的测试反馈）
        if (feedback.getCreatedBy() != null
                && !feedback.getCreatedBy().equals(UserContext.getUserId())) {
            notificationService.send(feedback.getCreatedBy(),
                    NotificationType.FEEDBACK_STATUS,
                    "候选问题被驳回",
                    "你录入的反馈 " + feedback.getFeedbackNo()
                            + " 参与归纳的候选问题被 PM 驳回"
                            + (StringUtils.hasText(rejectReason)
                                    ? "：" + rejectReason : "")
                            + "，反馈已回到待归类池，将重新参与归纳",
                    "/feedbacks/" + feedbackId);
        }
    }

    /**
     * 查询问题关联反馈的原文
     *
     * @param issueId 问题 ID
     * @return 反馈原文列表
     */
    private List<String> loadLinkedFeedbackContents(Long issueId) {

        List<FeedbackIssueLink> links = feedbackIssueLinkMapper.selectList(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getIssueId, issueId));

        if (CollectionUtils.isEmpty(links)) {
            return Collections.emptyList();
        }

        List<Long> ids = links.stream()
                .map(FeedbackIssueLink::getFeedbackId)
                .distinct()
                .collect(Collectors.toList());

        return feedbackMapper.selectBatchIds(ids).stream()
                .map(Feedback::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 批量查询用户实体
     *
     * <p>一次 IN 查询取出全部用户，避免逐条查询。
     * 日志列表需要同时展示操作人姓名与角色，因此这里返回实体而非姓名映射。
     *
     * @param userIds 用户 ID 列表
     * @return 用户ID → 用户实体
     */
    private Map<Long, SysUser> resolveUsers(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> user,
                        (a, b) -> a));
    }

    /**
     * 批量查询用户姓名
     *
     * @param userIds 用户 ID 列表
     * @return 用户ID → 姓名
     */
    private Map<Long, String> resolveUserNames(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return resolveUsers(userIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getDisplayName(),
                        (a, b) -> a));
    }

    /**
     * 实体转视图对象（无处理人姓名）
     */
    private AiCandidateVO toVO(AiCandidateIssue entity) {
        return toVO(entity, Collections.emptyMap());
    }

    /**
     * 实体转视图对象
     *
     * @param entity    候选卡片实体
     * @param userNames 用户ID → 姓名
     * @return 视图对象
     */
    private AiCandidateVO toVO(AiCandidateIssue entity,
                               Map<Long, String> userNames) {
        return AiCandidateVO.builder()
                .id(entity.getId())
                .batchNo(entity.getBatchNo())
                .title(entity.getTitle())
                .phenomenon(entity.getPhenomenon())
                .userDemand(entity.getUserDemand())
                .module(entity.getModule())
                .moduleLabel(StatusLabelUtil.moduleLabel(entity.getModule()))
                .aiConfidence(entity.getAiConfidence())
                .aiReason(entity.getAiReason())
                .relatedFeedbackIds(
                        JsonUtil.jsonToLongList(entity.getRelatedFeedbackIds()))
                .status(entity.getStatus())
                .statusLabel(StatusLabelUtil.candidateStatusLabel(entity.getStatus()))
                .handledBy(entity.getHandledBy())
                .handledByName(userNames.getOrDefault(entity.getHandledBy(), "—"))
                .handleRemark(entity.getHandleRemark())
                .handleTime(entity.getHandleTime())
                .generatedIssueId(entity.getGeneratedIssueId())
                .createTime(entity.getCreateTime())
                .build();
    }

    /**
     * 日志实体转视图对象
     *
     * @param entity 日志实体
     * @param users  用户ID → 用户实体
     * @return 视图对象
     */
    private AiLogVO toVO(AiGenerationLog entity, Map<Long, SysUser> users) {

        SysUser operator = entity.getOperatorId() == null
                ? null : users.get(entity.getOperatorId());

        AiBizType bizType = AiBizType.of(entity.getBizType());
        AiProvider provider = AiProvider.of(entity.getProvider());
        AiLogStatus status = AiLogStatus.of(entity.getStatus());

        return AiLogVO.builder()
                .id(entity.getId())
                .bizType(entity.getBizType())
                .bizTypeLabel(bizType == null ? entity.getBizType() : bizType.getLabel())
                .bizId(entity.getBizId())
                .batchNo(entity.getBatchNo())
                .provider(entity.getProvider())
                .providerLabel(provider.getLabel())
                .model(entity.getModel())
                .inputSnapshot(entity.getInputSnapshot())
                .outputRaw(entity.getOutputRaw())
                .status(entity.getStatus())
                .statusLabel(status.getLabel())
                .errorMsg(entity.getErrorMsg())
                .costMs(entity.getCostMs())
                .operatorName(operator == null ? "—" : operator.getDisplayName())
                .operatorRole(operator == null ? "—" : operator.getRole())
                .traceId(org.slf4j.MDC.get("traceId"))
                .createTime(entity.getCreateTime())
                .build();
    }

    /**
     * 文本去空白，空白返回 null
     */
    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 截断长文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…(已截断)";
    }

    /**
     * 对象序列化后截断
     */
    private String truncate(Object obj, int maxLen) {
        if (obj == null) {
            return null;
        }
        String json = JsonUtil.toJson(obj);
        return json == null ? null : truncate(json, maxLen);
    }
}
