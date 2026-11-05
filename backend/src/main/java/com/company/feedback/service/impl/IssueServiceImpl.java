package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.feedback.common.JsonUtil;
import com.company.feedback.common.PageResult;
import com.company.feedback.common.ResultCode;
import com.company.feedback.common.StatusLabelUtil;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.IssueCreateRequest;
import com.company.feedback.dto.IssueQueryRequest;
import com.company.feedback.dto.LinkFeedbackRequest;
import com.company.feedback.entity.AiCandidateIssue;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.FeedbackIssueLink;
import com.company.feedback.entity.Issue;
import com.company.feedback.entity.SysUser;
import com.company.feedback.enums.CandidateStatus;
import com.company.feedback.enums.FeedbackModule;
import com.company.feedback.enums.FeedbackStatus;
import com.company.feedback.enums.IssueSourceType;
import com.company.feedback.enums.LinkType;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.AiCandidateIssueMapper;
import com.company.feedback.mapper.FeedbackIssueLinkMapper;
import com.company.feedback.mapper.FeedbackMapper;
import com.company.feedback.mapper.IssueMapper;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.service.IssueService;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.IssueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问题服务实现
 *
 * <p><b>本类承载本项目的核心业务规则：AI 产出与人工确认结果的分离与可回查</b>。
 *
 * <p>具体体现在 {@link #confirmFromCandidate} 方法：
 * 确认时把 AI 原话写入 aiDraftContent（此后永不覆盖），
 * 把 PM 定稿写入 finalContent，两者并存，
 * 并通过比对两者自动判定 PM 是否做了修改（CONFIRMED vs MODIFIED）。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private final IssueMapper issueMapper;
    private final FeedbackMapper feedbackMapper;
    private final FeedbackIssueLinkMapper feedbackIssueLinkMapper;
    private final AiCandidateIssueMapper candidateIssueMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    // 说明：问题来源原先是这里的两行 String 常量，阶段 5 契约核对时
    // 发现前端也在硬编码同样的 "AI"/"MANUAL" 字符串，两侧没有任何约束关系，
    // 拼错不会报错只会静默产生脏数据。现已统一收敛到 IssueSourceType 枚举。

    // ==================== 写入 ====================

    /**
     * PM 手工创建问题
     *
     * <p>与 AI 确认生成路径的区别仅在于：
     * sourceType=MANUAL、aiDraftContent 为空。
     * 两条路径最终都落在同一张 issue 表，
     * 保证统计口径统一——「不管来源如何，已确认问题就是已确认问题」。
     *
     * @param request 创建参数
     * @return 新建问题的 ID
     */
    @Override
    public Long create(IssueCreateRequest request) {

        Issue entity = new Issue();
        entity.setIssueNo(generateIssueNo());
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setModule(FeedbackModule.of(request.getModule()).getCode());
        // 手工创建没有 AI 参与，草稿字段保持为空
        entity.setAiDraftContent(null);
        entity.setFinalContent(trimToNull(request.getFinalContent()));
        entity.setAcceptanceCriteria(trimToNull(request.getAcceptanceCriteria()));
        entity.setSourceType(IssueSourceType.MANUAL.getCode());
        entity.setConfirmedBy(UserContext.getUserId());
        entity.setConfirmTime(LocalDateTime.now());
        entity.setVersion(0);

        issueMapper.insert(entity);

        log.info("PM手工创建问题成功, id={}, no={}", entity.getId(), entity.getIssueNo());
        return entity.getId();
    }

    /**
     * 由 AI 候选卡片确认生成正式问题
     *
     * <p><b>这是整个系统最重要的一个方法</b>，
     * 它完成了从「AI 草稿」到「人工确认的业务事实」的跨越。
     *
     * <p><b>关键设计：AI 原话与 PM 定稿并存</b>
     * <pre>
     *   aiDraftContent = AI 生成的完整内容（永不覆盖）
     *   finalContent   = PM 确认/修改后的内容
     * </pre>
     * 这样即使 PM 把 AI 的建议改得面目全非，
     * 事后依然能回答"AI 当初到底是怎么说的"。
     *
     * <p><b>状态自动判定</b>：
     * 如果 PM 未做任何改动 → CONFIRMED（已确认）
     * 如果 PM 修改了标题或描述 → MODIFIED（修改后确认）
     * 这个判定结果本身就是「AI 生成内容与人工确认结果可区分」的量化证据。
     *
     * @param candidateId         候选卡片 ID
     * @param finalTitle          PM 确认后的标题
     * @param finalDesc           PM 确认后的描述
     * @param finalContent        PM 定稿的需求说明
     * @param acceptanceCriteria  PM 填写的验收条件
     * @return 新建问题实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Issue confirmFromCandidate(Long candidateId,
                                      String finalTitle,
                                      String finalDesc,
                                      String finalContent,
                                      String acceptanceCriteria) {

        // 1. 加载候选卡片并校验状态
        AiCandidateIssue candidate = candidateIssueMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候选问题卡片不存在");
        }
        if (!CandidateStatus.DRAFT.getCode().equals(candidate.getStatus())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已处理过，不能重复确认");
        }

        // 2. 拼接 AI 原始内容（保留原话，便于复盘与追责）
        String aiDraft = buildAiDraftSnapshot(candidate);

        // 3. 判断 PM 是否修改了 AI 的建议
        //
        // ⚠️ 比较基准必须是「AI 原稿按同样规则拼出来的描述」，
        //    而不是候选卡片的 phenomenon 字段。
        //    因为前端确认弹窗里展示的描述是「【现象】…【诉求】…」这种拼装格式，
        //    若拿 phenomenon 去比，PM 一个字没改也会被判成"已修改"，
        //    这个标记就失去了区分 AI 产出与人工决策的意义。
        String expectedDesc = buildAiDescriptionSnapshot(candidate);
        boolean modified = !safeEquals(candidate.getTitle(), finalTitle)
                || !safeEquals(expectedDesc, finalDesc);

        // 4. 生成正式问题
        //
        // 注意：aiDraftContent 写入后<b>永不覆盖</b>，
        // 后续 PM 无论怎么改，都只改 finalContent，AI 原话始终保留。
        Issue issue = new Issue();
        issue.setIssueNo(generateIssueNo());
        issue.setTitle(finalTitle.trim());
        issue.setDescription(trimToNull(finalDesc));
        issue.setModule(candidate.getModule());
        issue.setAiDraftContent(aiDraft);
        issue.setFinalContent(StringUtils.hasText(finalContent)
                ? finalContent.trim()
                : finalTitle.trim());
        issue.setAcceptanceCriteria(trimToNull(acceptanceCriteria));
        issue.setSourceType(IssueSourceType.AI.getCode());
        issue.setConfirmedBy(UserContext.getUserId());
        issue.setConfirmTime(LocalDateTime.now());
        issue.setVersion(0);

        issueMapper.insert(issue);

        // 5. 回写候选卡片状态
        AiCandidateIssue update = new AiCandidateIssue();
        update.setId(candidateId);
        update.setStatus(modified
                ? CandidateStatus.MODIFIED.getCode()
                : CandidateStatus.CONFIRMED.getCode());
        update.setHandledBy(UserContext.getUserId());
        update.setHandleTime(LocalDateTime.now());
        update.setGeneratedIssueId(issue.getId());
        update.setVersion(candidate.getVersion());

        int rows = candidateIssueMapper.updateById(update);
        if (rows == 0) {
            // 走到这里说明并发确认，乐观锁拦截
            throw new BizException(ResultCode.CONFLICT,
                    "该候选卡片已被他人处理，请刷新后重试");
        }

        // 6. 建立「反馈 → 问题」关联，来源标记为 AI
        List<Long> feedbackIds = JsonUtil.jsonToLongList(
                candidate.getRelatedFeedbackIds());
        if (!CollectionUtils.isEmpty(feedbackIds)) {
            for (Long feedbackId : feedbackIds) {
                insertLinkIfAbsent(feedbackId, issue.getId(),
                        candidateId, LinkType.AI.getCode());
            }
        }

        log.info("候选卡片确认生成问题成功, candidateId={}, issueId={}, modified={}",
                candidateId, issue.getId(), modified);

        return issue;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询问题列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<IssueVO> page(IssueQueryRequest query) {

        long pageNo = (query.getPageNo() == null || query.getPageNo() < 1)
                ? 1L : query.getPageNo();
        long pageSize = (query.getPageSize() == null || query.getPageSize() < 1)
                ? 10L : query.getPageSize();

        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(StringUtils.hasText(query.getModule()),
                        Issue::getModule, query.getModule())
                // 经枚举归一化：传入非法值时 of() 会兜底为 MANUAL，
                // 保证查询条件永远只会落在合法枚举值上，不会查出脏数据
                .eq(StringUtils.hasText(query.getSourceType()),
                        Issue::getSourceType,
                        IssueSourceType.of(query.getSourceType()).getCode())
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(Issue::getTitle, query.getKeyword())
                                .or()
                                .like(Issue::getDescription, query.getKeyword())
                                .or()
                                .like(Issue::getIssueNo, query.getKeyword()))
                .orderByDesc(Issue::getCreateTime);

        IPage<Issue> pageResult = issueMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);

        // 批量补齐：确认人姓名 + 关联反馈ID列表 + 是否改过AI草稿，均避免 N+1 查询
        Map<Long, String> userNames = resolveUserNames(pageResult.getRecords());
        Map<Long, List<Long>> feedbackMap =
                resolveFeedbackIds(pageResult.getRecords());
        Map<Long, Boolean> modifiedMap =
                resolveModifiedMap(pageResult.getRecords());

        return PageResult.of(pageResult, entity -> toVO(
                entity,
                userNames.get(entity.getConfirmedBy()),
                feedbackMap.getOrDefault(entity.getId(), List.of()),
                modifiedMap.get(entity.getId())
        ));
    }

    /**
     * 查询问题详情
     *
     * @param id 问题 ID
     * @return 问题视图对象
     */
    @Override
    public IssueVO detail(Long id) {

        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在或已被删除");
        }

        String confirmedByName = null;
        if (issue.getConfirmedBy() != null) {
            SysUser user = sysUserMapper.selectById(issue.getConfirmedBy());
            confirmedByName = user == null ? null : user.getDisplayName();
        }

        List<Long> feedbackIds = feedbackIssueLinkMapper
                .selectList(new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getIssueId, id))
                .stream()
                .map(FeedbackIssueLink::getFeedbackId)
                .toList();

        return toVO(issue, confirmedByName, feedbackIds,
                resolveModifiedMap(List.of(issue)).get(issue.getId()));
    }

    // ==================== 关联关系维护 ====================

    /**
     * 手工关联反馈到问题
     *
     * <p><b>幂等设计</b>：数据库有 (feedback_id, issue_id) 唯一索引，
     * 这里先查后插，已经存在的关联自动跳过，不报错。
     * 理由：PM 可能误选了已经关联过的反馈，
     * 报错会打断操作流程，静默跳过更符合实际使用习惯。
     *
     * @param id      问题 ID
     * @param request 关联参数
     * @return 实际新增的关联条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int linkFeedbacks(Long id, LinkFeedbackRequest request) {

        // 1. 校验问题存在
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "问题不存在或已被删除");
        }

        List<Long> feedbackIds = request.getFeedbackIds();
        if (CollectionUtils.isEmpty(feedbackIds)) {
            return 0;
        }

        int count = 0;
        for (Long feedbackId : feedbackIds) {
            if (insertLinkIfAbsent(feedbackId, id, null, LinkType.PM.getCode())) {
                count++;
            }
        }

        log.info("手工关联反馈成功, issueId={}, 新增{}条", id, count);
        return count;
    }

    /**
     * 解除反馈与问题的关联
     *
     * <p><b>为什么要检查"是否还有其他关联"</b>：
     * 一条反馈可能同时关联多个问题。
     * 解除其中一个关联时，它仍然属于另一个问题，
     * 此时状态应保持 LINKED，不能贸然回退为 NEW，
     * 否则会造成"明明还挂着问题，却显示未归类"的矛盾。
     *
     * @param issueId    问题 ID
     * @param feedbackId 反馈 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkFeedback(Long issueId, Long feedbackId) {

        // 1. 删除关联关系
        long deleted = feedbackIssueLinkMapper.delete(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getIssueId, issueId)
                        .eq(FeedbackIssueLink::getFeedbackId, feedbackId)
        );

        if (deleted == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "该关联关系不存在");
        }

        // 2. 检查该反馈是否还有其他关联
        long remaining = feedbackIssueLinkMapper.selectCount(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getFeedbackId, feedbackId)
        );

        // 3. 若无任何关联，状态回退为「未归类」，重新进入待归类池
        if (remaining == 0) {
            Feedback feedback = feedbackMapper.selectById(feedbackId);
            if (feedback != null
                    && FeedbackStatus.LINKED.getCode().equals(feedback.getStatus())) {

                Feedback update = new Feedback();
                update.setId(feedbackId);
                update.setStatus(FeedbackStatus.NEW.getCode());
                update.setVersion(feedback.getVersion());
                feedbackMapper.updateById(update);
            }
        }

        log.info("解除反馈关联成功, issueId={}, feedbackId={}, 剩余关联{}条",
                issueId, feedbackId, remaining);
    }

    // ==================== 私有方法 ====================

    /**
     * 插入一条反馈-问题关联（已存在则跳过）
     *
     * <p>本方法同时负责把反馈状态推进为 LINKED，
     * 保证"有关联"与"状态为已关联"这两件事永远一致。
     *
     * @param feedbackId  反馈 ID
     * @param issueId     问题 ID
     * @param candidateId 来源候选卡片 ID，手工关联时为 null
     * @param linkType    关联类型：AI / PM
     * @return 新增返回 true，已存在返回 false
     */
    private boolean insertLinkIfAbsent(Long feedbackId, Long issueId,
                                       Long candidateId, String linkType) {

        // 1. 校验反馈存在
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BizException(ResultCode.NOT_FOUND,
                        "反馈不存在：" + feedbackId);
        }

        // 2. 已存在则跳过（幂等）
        long exists = feedbackIssueLinkMapper.selectCount(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getFeedbackId, feedbackId)
                        .eq(FeedbackIssueLink::getIssueId, issueId)
        );
        if (exists > 0) {
            return false;
        }

        // 3. 新增关联
        FeedbackIssueLink link = new FeedbackIssueLink();
        link.setFeedbackId(feedbackId);
        link.setIssueId(issueId);
        link.setCandidateId(candidateId);
        link.setLinkType(linkType);
        feedbackIssueLinkMapper.insert(link);

        // 4. 同步推进反馈状态：NEW → LINKED
        if (FeedbackStatus.NEW.getCode().equals(feedback.getStatus())) {
            Feedback update = new Feedback();
            update.setId(feedbackId);
            update.setStatus(FeedbackStatus.LINKED.getCode());
            update.setVersion(feedback.getVersion());
            feedbackMapper.updateById(update);
        }

        // 5. 通知录入人「反馈已采纳」（操作人是录入人本人时不发）
        //    无论来自 AI 候选确认还是 PM 手工关联，对客服来说都是同一个结果：
        //    自己报上去的问题被公司认了，应该有明确的回音。
        if (feedback.getCreatedBy() != null
                && !feedback.getCreatedBy().equals(UserContext.getUserId())) {
            Issue issue = issueMapper.selectById(issueId);
            String issueTitle = issue == null ? "" : issue.getTitle();
            notificationService.send(feedback.getCreatedBy(),
                    NotificationType.FEEDBACK_STATUS,
                    "反馈已采纳",
                    "你录入的反馈 " + feedback.getFeedbackNo()
                            + " 已被采纳，关联为问题「" + issueTitle + "」",
                    "/issues/" + issueId);
        }

        return true;
    }

    /**
     * 生成问题编号
     *
     * <p>格式：IS-0001，序号取当前最大 ID + 1
     *
     * @return 问题编号
     */
    private String generateIssueNo() {
        Issue last = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId)
                        .orderByDesc(Issue::getId)
                        .last("LIMIT 1")
        );
        long seq = (last == null || last.getId() == null) ? 1L : last.getId() + 1L;
        return "IS-" + String.format("%04d", seq);
    }

    /**
     * 批量查询确认人姓名
     *
     * @param issues 问题列表
     * @return 用户ID → 姓名的映射
     */
    private Map<Long, String> resolveUserNames(List<Issue> issues) {
        if (CollectionUtils.isEmpty(issues)) {
            return Map.of();
        }
        List<Long> userIds = issues.stream()
                .map(Issue::getConfirmedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        SysUser::getDisplayName,
                        (a, b) -> a
                ));
    }

    /**
     * 批量查询每个问题关联的反馈 ID
     *
     * <p>一次 IN 查询取出全部关联，再在内存中按 issueId 分组，
     * 避免每渲染一条问题就查一次数据库。
     *
     * @param issues 问题列表
     * @return 问题ID → 反馈ID列表
     */
    private Map<Long, List<Long>> resolveFeedbackIds(List<Issue> issues) {
        if (CollectionUtils.isEmpty(issues)) {
            return Map.of();
        }
        List<Long> issueIds = issues.stream().map(Issue::getId).toList();

        List<FeedbackIssueLink> links = feedbackIssueLinkMapper.selectList(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .in(FeedbackIssueLink::getIssueId, issueIds)
        );

        Map<Long, List<Long>> map = new java.util.HashMap<>();
        for (FeedbackIssueLink link : links) {
            map.computeIfAbsent(link.getIssueId(), k -> new ArrayList<>())
                    .add(link.getFeedbackId());
        }
        return map;
    }

    /**
     * 实体转视图对象
     *
     * <p><b>本方法是「AI 草稿与人工定稿可对比」这条设计的展现层落点</b>：
     * 返回的 VO 同时携带 {@code aiDraftContent}（AI 原话，只读快照）
     * 与 {@code finalContent}（PM 定稿），前端可直接并排展示两者差异。
     * 这样评审时能一眼看出「AI 说了什么、人改成了什么」，
     * 而不是只看得到一个最终结果。
     *
     * @param issue           问题实体
     * @param confirmedByName 确认人姓名，可为 null
     * @param feedbackIds     关联的反馈 ID 列表
     * @param modifiedFromAi  是否在 AI 建议基础上做过人工修改，null 表示非 AI 来源
     * @return 问题视图对象
     */
    private IssueVO toVO(Issue issue, String confirmedByName,
                         List<Long> feedbackIds, Boolean modifiedFromAi) {
        return IssueVO.builder()
                .id(issue.getId())
                .issueNo(issue.getIssueNo())
                .title(issue.getTitle())
                .description(issue.getDescription())
                .module(issue.getModule())
                .moduleLabel(StatusLabelUtil.moduleLabel(issue.getModule()))
                .aiDraftContent(issue.getAiDraftContent())
                .finalContent(issue.getFinalContent())
                .acceptanceCriteria(issue.getAcceptanceCriteria())
                .sourceType(issue.getSourceType())
                .modifiedFromAi(modifiedFromAi)
                .confirmedBy(issue.getConfirmedBy())
                .confirmedByName(confirmedByName)
                .confirmTime(issue.getConfirmTime())
                .createTime(issue.getCreateTime())
                .feedbackIds(feedbackIds == null ? List.of() : feedbackIds)
                .build();
    }

    /**
     * 批量查询「是否改过 AI 草稿」标记
     *
     * <p><b>为什么不在 issue 表上加字段</b>：
     * 「改了还是没改」这个事实在确认那一刻就已确定，
     * 并已写进候选卡片的 status（MODIFIED / CONFIRMED）。
     * 再在 issue 表冗余一个布尔字段，等于把同一事实存两份，
     * 一旦两处不一致就无法判断谁对。
     * 因此这里通过 generated_issue_id 反查候选卡片，
     * 用一次 IN 查询批量取回，不会有 N+1 问题。
     *
     * @param issues 问题列表
     * @return 问题ID → 是否修改过；非 AI 来源的问题不会出现在结果中
     */
    private Map<Long, Boolean> resolveModifiedMap(List<Issue> issues) {
        if (CollectionUtils.isEmpty(issues)) {
            return Map.of();
        }
        List<Long> aiIssueIds = issues.stream()
                .filter(i -> IssueSourceType.AI.getCode().equals(i.getSourceType()))
                .map(Issue::getId)
                .toList();
        if (aiIssueIds.isEmpty()) {
            return Map.of();
        }

        List<AiCandidateIssue> candidates = candidateIssueMapper.selectList(
                new LambdaQueryWrapper<AiCandidateIssue>()
                        .select(AiCandidateIssue::getGeneratedIssueId,
                                AiCandidateIssue::getStatus)
                        .in(AiCandidateIssue::getGeneratedIssueId, aiIssueIds)
        );

        Map<Long, Boolean> map = new HashMap<>();
        for (AiCandidateIssue c : candidates) {
            if (c.getGeneratedIssueId() != null) {
                map.put(c.getGeneratedIssueId(),
                        CandidateStatus.MODIFIED.getCode().equals(c.getStatus()));
            }
        }
        return map;
    }

    /**
     * 拼接 AI 原始内容快照
     *
     * <p><b>为什么要拼成带标签的文本而不是直接存原文</b>：
     * AI 返回的是 JSON，内部字段（标题/现象/诉求/理由）分散。
     * 存成带标签的纯文本，PM 在页面上能直接读懂，
     * 同时也保留了原始结构，便于后续做差异比对。
     *
     * @param candidate 候选卡片
     * @return AI 原始内容快照
     */
    private String buildAiDraftSnapshot(AiCandidateIssue candidate) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AI 候选标题] ").append(nvl(candidate.getTitle())).append('\n');
        sb.append("[AI 现象归纳] ").append(nvl(candidate.getPhenomenon())).append('\n');
        sb.append("[AI 诉求归纳] ").append(nvl(candidate.getUserDemand())).append('\n');
        sb.append("[AI 归类理由] ").append(nvl(candidate.getAiReason())).append('\n');
        if (candidate.getAiConfidence() != null) {
            sb.append("[AI 置信度] ").append(candidate.getAiConfidence());
        }
        return sb.toString().trim();
    }

    /**
     * 按前端确认弹窗的展示格式，把 AI 原稿拼成"预期描述"
     *
     * <p>用途：判断 PM 是否修改了 AI 的建议。
     * 只有两边使用<b>完全相同的拼装规则</b>，
     * 比较结果才有意义——PM 原样点确认时，两侧字符串必须严格相等。
     *
     * @param candidate AI 候选卡片
     * @return 预期描述文本
     */
    private String buildAiDescriptionSnapshot(AiCandidateIssue candidate) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(candidate.getPhenomenon())) {
            parts.add("【现象】" + candidate.getPhenomenon().trim());
        }
        if (StringUtils.hasText(candidate.getUserDemand())) {
            parts.add("【诉求】" + candidate.getUserDemand().trim());
        }
        return String.join("\n\n", parts);
    }

    /** 空值转空串 */
    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 去空格，空串转 null */
    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /**
     * 安全的字符串比较（均为空视为相同）
     *
     * @param a 字符串A
     * @param b 字符串B
     * @return 相同返回 true
     */
    private boolean safeEquals(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }
}
