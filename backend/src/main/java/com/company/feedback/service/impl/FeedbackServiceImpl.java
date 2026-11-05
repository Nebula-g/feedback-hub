package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.feedback.common.JsonUtil;
import com.company.feedback.common.PageResult;
import com.company.feedback.common.ResultCode;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.FeedbackBatchImportRequest;
import com.company.feedback.dto.FeedbackCreateRequest;
import com.company.feedback.dto.FeedbackQueryRequest;
import com.company.feedback.dto.FeedbackStatusUpdateRequest;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.SysUser;
import com.company.feedback.enums.FeedbackModule;
import com.company.feedback.enums.FeedbackSource;
import com.company.feedback.enums.FeedbackStatus;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.FeedbackMapper;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.service.FeedbackService;
import com.company.feedback.service.NotificationService;
import com.company.feedback.vo.FeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 反馈服务实现
 *
 * <p><b>本类是「原始反馈只读」这一核心规则的执行者</b>。
 * 请特别注意：整个类里<b>没有任何方法会去 setContent()</b>，
 * 也没有提供更新原文的入口。这不是遗漏，而是刻意的设计。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    // ==================== 查询 ====================

    /**
     * 分页查询反馈列表
     *
     * <p>查询条件全部为可选：
     * <ul>
     *   <li>module —— 功能模块精确匹配</li>
     *   <li>status —— 状态精确匹配</li>
     *   <li>source —— 来源精确匹配</li>
     *   <li>keyword —— 反馈原文或编号模糊匹配</li>
     * </ul>
     *
     * <p>默认按创建时间倒序，保证新录入的反馈排在最前面——
     * 这符合客服"刚录完想立刻看到"的实际使用习惯。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<FeedbackVO> page(FeedbackQueryRequest query) {

        // 1. 分页参数兜底，防止前端传 0 或负数导致 SQL 报错
        long pageNo = (query.getPageNo() == null || query.getPageNo() < 1)
                ? 1L : query.getPageNo();
        long pageSize = (query.getPageSize() == null || query.getPageSize() < 1)
                ? 10L : query.getPageSize();

        // 2. 构造查询条件
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(query.getModule()),
                        Feedback::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getStatus()),
                        Feedback::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getSource()),
                        Feedback::getSource, query.getSource())
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(Feedback::getContent, query.getKeyword())
                                .or()
                                .like(Feedback::getFeedbackNo, query.getKeyword()))
                .orderByDesc(Feedback::getCreateTime);

        // 3. 执行分页查询
        IPage<Feedback> pageResult = feedbackMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);

        // 4. 批量查询录入人姓名，避免 N+1 查询
        //    （如果循环里逐条查 sys_user，60 条反馈就要发 60 次 SQL）
        Map<Long, String> userNames = resolveUserNames(pageResult.getRecords());

        // 5. 转换为 VO
        return PageResult.of(pageResult,
                entity -> toVO(entity, userNames.get(entity.getCreatedBy())));
    }

    /**
     * 查询反馈详情
     *
     * @param id 反馈 ID
     * @return 反馈视图对象
     */
    @Override
    public FeedbackVO detail(Long id) {
        Feedback feedback = requireFeedback(id);
        SysUser creator = sysUserMapper.selectById(feedback.getCreatedBy());
        return toVO(feedback,
                creator == null ? null : creator.getDisplayName());
    }

    // ==================== 写入 ====================

    /**
     * 录入单条反馈
     *
     * <p><b>关键设计</b>：
     * <ul>
     *   <li>status 强制为 NEW，不接受前端传值</li>
     *   <li>createdBy 取当前登录人，不接受前端传值</li>
     *   <li>feedbackNo 由服务端按日期 + 序号生成</li>
     * </ul>
     *
     * @param request 录入参数
     * @return 新建反馈的 ID
     */
    @Override
    public Long create(FeedbackCreateRequest request) {
        Feedback entity = buildEntity(request);
        feedbackMapper.insert(entity);
        log.info("录入反馈成功, id={}, no={}, module={}",
                entity.getId(), entity.getFeedbackNo(), entity.getModule());
        return entity.getId();
    }

    /**
     * 批量导入反馈
     *
     * <p><b>为什么整个批次放在一个事务里</b>：
     * 批量导入是"要么全部成功，要么全部失败"的操作。
     * 如果导入 50 条时第 30 条失败，
     * 用户看到的应该是"导入失败，请检查数据"，
     * 而不是"前 29 条进去了，后面 21 条没进去"——
     * 后者会让用户搞不清到底导了多少，只能手工核对。
     *
     * @param request 批量导入参数
     * @return 新建反馈的 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchImport(FeedbackBatchImportRequest request) {

        List<Long> ids = new ArrayList<>(request.getItems().size());

        for (FeedbackCreateRequest item : request.getItems()) {
            Feedback entity = buildEntity(item);
            feedbackMapper.insert(entity);
            ids.add(entity.getId());
        }

        log.info("批量导入反馈成功, count={}", ids.size());
        return ids;
    }

    /**
     * 变更反馈归类状态
     *
     * <p><b>状态机校验（本项目的核心业务规则）</b>：
     * 不是"想改成什么就改成什么"，而是必须遵守预定义的流转路径。
     * 例如 NEW 只能到 LINKED 或 IGNORED，不能一步跳到 CLOSED。
     *
     * <p>为什么要在 Service 层再校验一遍而不只靠前端：
     * 前端的按钮禁用只是体验优化，绕过页面直接调接口就失效了。
     * 状态机校验必须放在服务端，这才是真正的约束。
     *
     * @param id      反馈 ID
     * @param request 状态变更参数
     */
    @Override
    public void updateStatus(Long id, FeedbackStatusUpdateRequest request) {

        Feedback feedback = requireFeedback(id);

        // 1. 目标状态合法性校验
        FeedbackStatus targetStatus = FeedbackStatus.of(request.getStatus());
        if (targetStatus == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "非法的目标状态：" + request.getStatus());
        }

        // 2. 状态机流转校验
        FeedbackStatus currentStatus = FeedbackStatus.of(feedback.getStatus());
        if (currentStatus == null) {
            throw new BizException(ResultCode.CONFLICT,
                    "当前反馈状态异常，请联系管理员");
        }

        if (!currentStatus.canTransitTo(targetStatus.getCode())) {
            throw new BizException(ResultCode.CONFLICT,
                    "不允许的状态流转：" + currentStatus.getLabel()
                            + " → " + targetStatus.getLabel());
        }

        // 3. 置为无效时必须填写说明
        //    理由：判定"无效"是一种主观判断，必须留痕，
        //    否则无法区分"真的无效"和"懒得处理"
        if (FeedbackStatus.IGNORED == targetStatus
                && !StringUtils.hasText(request.getRemark())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "将反馈置为无效时必须填写说明");
        }

        // 4. 执行更新（乐观锁：version 由 MyBatis-Plus 自动带上）
        Feedback update = new Feedback();
        update.setId(id);
        update.setStatus(targetStatus.getCode());
        update.setVersion(feedback.getVersion());

        int rows = feedbackMapper.updateById(update);
        if (rows == 0) {
            // 更新 0 行只有一种可能：version 不匹配，即他人已并发修改
            throw new BizException(ResultCode.CONFLICT,
                    "该反馈已被他人修改，请刷新后重试");
        }

        log.info("反馈状态变更成功, id={}, {} → {}",
                id, currentStatus.getCode(), targetStatus.getCode());

        // 通知录入人（操作人是录入人本人时不发）
        // 文案按语义区分「采纳 / 驳回」，客服一眼知道结果，不用猜状态码
        if (feedback.getCreatedBy() != null
                && !feedback.getCreatedBy().equals(UserContext.getUserId())) {
            String title;
            String content;
            if (FeedbackStatus.LINKED == targetStatus) {
                title = "反馈已采纳";
                content = "你录入的反馈 " + feedback.getFeedbackNo()
                        + " 已被采纳并关联为问题";
            } else if (FeedbackStatus.IGNORED == targetStatus) {
                title = "反馈被驳回";
                content = "你录入的反馈 " + feedback.getFeedbackNo() + " 被驳回："
                        + (StringUtils.hasText(request.getRemark())
                                ? request.getRemark() : "未说明原因");
            } else {
                title = "反馈状态变更";
                content = "你录入的反馈 " + feedback.getFeedbackNo()
                        + " 状态变更为 " + targetStatus.getLabel();
            }
            notificationService.send(feedback.getCreatedBy(),
                    NotificationType.FEEDBACK_STATUS,
                    title, content, "/feedbacks/" + id);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 把录入请求转换为实体
     *
     * <p>在这里统一完成：编号生成、状态初始化、录入人赋值、图片列表序列化。
     *
     * @param request 录入参数
     * @return 待插入的实体
     */
    private Feedback buildEntity(FeedbackCreateRequest request) {

        // 模块代码校验：非法值统一归入 OTHER，保证后续聚类分组不会拿到 null
        String module = FeedbackModule.isValid(request.getModule())
                ? request.getModule().trim().toUpperCase()
                : FeedbackModule.OTHER.getCode();

        String source = FeedbackSource.isValid(request.getSource())
                ? request.getSource().trim().toUpperCase()
                : FeedbackSource.TICKET.getCode();

        Feedback entity = new Feedback();
        entity.setFeedbackNo(generateFeedbackNo());
        entity.setContent(request.getContent().trim());
        entity.setModule(module);
        entity.setSource(source);
        entity.setCustomerTag(request.getCustomerTag());
        entity.setImageUrls(JsonUtil.listToJson(request.getImageUrls()));
        // 新录入的反馈一律为「未归类」，等待 PM 处理
        entity.setStatus(FeedbackStatus.NEW.getCode());
        // 录入人取当前登录用户，防止冒用他人身份
        entity.setCreatedBy(UserContext.getUserId());
        entity.setVersion(0);

        return entity;
    }

    /**
     * 生成反馈编号
     *
     * <p>格式：FB + yyyyMMdd + "-" + 四位序号，如 FB20261026-0001
     *
     * <p><b>序号生成策略</b>：取当前表中最大 ID + 1。
     *
     * <p>这个做法在高并发下理论上存在重复风险（两个请求同时读到同一个 maxId），
     * 但 feedback_no 上有唯一索引兜底——一旦重复，数据库会抛唯一键冲突，
     * 事务回滚，不会真的产生脏数据。
     * 对于本项目的演示场景（单人操作或低并发）已经足够，
     * 生产环境应改用独立的序列表或 Redis 自增。
     *
     * @return 反馈编号
     */
    private String generateFeedbackNo() {

        Feedback last = feedbackMapper.selectOne(
                new LambdaQueryWrapper<Feedback>()
                        .select(Feedback::getId)
                        .orderByDesc(Feedback::getId)
                        .last("LIMIT 1")
        );

        long seq = (last == null || last.getId() == null) ? 1L : last.getId() + 1L;
        String datePart = LocalDate.now()
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        return "FB" + datePart + "-" + String.format("%04d", seq);
    }

    /**
     * 按 ID 查询反馈，不存在则抛异常
     *
     * @param id 反馈 ID
     * @return 反馈实体
     */
    private Feedback requireFeedback(Long id) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BizException(ResultCode.NOT_FOUND, "反馈不存在或已被删除");
        }
        return feedback;
    }

    /**
     * 批量查询录入人姓名
     *
     * <p>这是为了避免 N+1 查询问题：先把这批反馈的 createdBy 收集成 Set，
     * 用一次 IN 查询拿到全部姓名，再在内存里做映射。
     *
     * @param feedbacks 反馈列表
     * @return 用户ID → 姓名的映射
     */
    private Map<Long, String> resolveUserNames(List<Feedback> feedbacks) {

        if (feedbacks == null || feedbacks.isEmpty()) {
            return Map.of();
        }

        // 收集去重后的录入人ID
        List<Long> userIds = feedbacks.stream()
                .map(Feedback::getCreatedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (userIds.isEmpty()) {
            return Map.of();
        }

        // 一次 IN 查询拿全部
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);

        return users.stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        SysUser::getDisplayName,
                        // 理论上不会重复（主键唯一），这里兜底防止 toMap 抛异常
                        (a, b) -> a
                ));
    }

    /**
     * 实体转 VO
     *
     * @param entity      反馈实体
     * @param creatorName 录入人姓名（可能为 null）
     * @return 反馈视图对象
     */
    private FeedbackVO toVO(Feedback entity, String creatorName) {
        return FeedbackVO.builder()
                .id(entity.getId())
                .feedbackNo(entity.getFeedbackNo())
                .content(entity.getContent())
                .module(entity.getModule())
                .moduleLabel(FeedbackModule.of(entity.getModule()).getLabel())
                .source(entity.getSource())
                .sourceLabel(FeedbackSource.of(entity.getSource()).getLabel())
                .customerTag(entity.getCustomerTag())
                .imageUrls(JsonUtil.jsonToList(entity.getImageUrls()))
                .status(entity.getStatus())
                .statusLabel(FeedbackStatus.of(entity.getStatus()).getLabel())
                .createdBy(entity.getCreatedBy())
                .createdByName(creatorName)
                .createTime(entity.getCreateTime())
                .build();
    }
}
