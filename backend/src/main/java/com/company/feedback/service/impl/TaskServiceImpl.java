package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.feedback.common.PageResult;
import com.company.feedback.common.ResultCode;
import com.company.feedback.common.StatusLabelUtil;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.TaskAssignRequest;
import com.company.feedback.dto.TaskCreateRequest;
import com.company.feedback.dto.TaskPriorityUpdateRequest;
import com.company.feedback.dto.TaskQueryRequest;
import com.company.feedback.dto.TaskStatusUpdateRequest;
import com.company.feedback.entity.Feedback;
import com.company.feedback.entity.FeedbackIssueLink;
import com.company.feedback.entity.ImprovementTask;
import com.company.feedback.entity.Issue;
import com.company.feedback.entity.SysUser;
import com.company.feedback.entity.TaskEvent;
import com.company.feedback.enums.FeedbackModule;
import com.company.feedback.enums.FeedbackStatus;
import com.company.feedback.enums.RoleEnum;
import com.company.feedback.enums.TaskPriority;
import com.company.feedback.enums.TaskStatus;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.FeedbackIssueLinkMapper;
import com.company.feedback.mapper.FeedbackMapper;
import com.company.feedback.mapper.ImprovementTaskMapper;
import com.company.feedback.mapper.IssueMapper;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.mapper.TaskEventMapper;
import com.company.feedback.enums.NotificationType;
import com.company.feedback.service.NotificationService;
import com.company.feedback.service.TaskService;
import com.company.feedback.vo.TaskEventVO;
import com.company.feedback.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 改进任务服务实现
 *
 * <p><b>本类是三条权限硬规则（R1/R2/R5）的执行者</b>。
 *
 * <p>最核心的方法是 {@link #transit}，
 * 它把"状态机校验 + 角色校验 + 数据归属校验 + 乐观锁并发控制 + 事件留痕"
 * 五件事串在了一个事务里，任何一个环节不满足都不会产生脏数据。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final ImprovementTaskMapper taskMapper;
    private final IssueMapper issueMapper;
    private final FeedbackMapper feedbackMapper;
    private final FeedbackIssueLinkMapper feedbackIssueLinkMapper;
    private final TaskEventMapper taskEventMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    // ==================== 写入 ====================

    /**
     * PM 创建改进任务
     *
     * <p><b>校验链</b>：
     * <ol>
     *   <li>关联的问题必须存在</li>
     *   <li>优先级必须是合法的 P0/P1/P2</li>
     *   <li>负责人（若填写）必须是 DEV 角色</li>
     * </ol>
     *
     * <p>为什么需求说明默认取问题的定稿内容：
     * PM 在确认问题时已经把需求想清楚了，
     * 建任务时再手打一遍既浪费时间又容易出错。
     * 允许在建立任务时覆盖，兼顾灵活性。
     *
     * @param request 创建参数
     * @return 新建任务的 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaskCreateRequest request) {

        // 1. 校验关联问题存在
        Issue issue = issueMapper.selectById(request.getIssueId());
        if (issue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "关联的问题不存在");
        }

        // 2. 校验优先级合法
        if (!TaskPriority.isValid(request.getPriority())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "非法的优先级：" + request.getPriority());
        }

        // 3. 校验负责人必须是开发测试角色
        if (request.getAssigneeId() != null) {
            requireDevUser(request.getAssigneeId());
        }

        // 4. 需求说明优先取请求值，否则回落到问题的定稿内容
        String requirement = StringUtils.hasText(request.getRequirement())
                ? request.getRequirement().trim()
                : issue.getFinalContent();

        ImprovementTask entity = new ImprovementTask();
        entity.setTaskNo(generateTaskNo());
        entity.setIssueId(issue.getId());
        entity.setTitle(request.getTitle().trim());
        entity.setRequirement(requirement);
        entity.setAcceptanceCriteria(trimToNull(request.getAcceptanceCriteria()));
        // 优先级完全来自 PM 的手动选择，无任何自动计算
        entity.setPriority(request.getPriority().trim().toUpperCase());
        entity.setPriorityReason(trimToNull(request.getPriorityReason()));
        entity.setAssigneeId(request.getAssigneeId());
        entity.setStatus(TaskStatus.TODO.getCode());
        entity.setCreatedBy(UserContext.getUserId());
        entity.setVersion(0);

        taskMapper.insert(entity);

        // 5. 记录创建事件，作为时间线的起点
        appendEvent(entity.getId(), null, TaskStatus.TODO.getCode(),
                "创建改进任务，优先级 " + entity.getPriority());

        log.info("创建改进任务成功, id={}, no={}, priority={}",
                entity.getId(), entity.getTaskNo(), entity.getPriority());

        return entity.getId();
    }

    /**
     * 推进任务状态
     *
     * <p><b>本方法是本项目的状态机中枢</b>，依次执行五道校验：
     * <ol>
     *   <li>目标状态合法性</li>
     *   <li>状态机流转规则（禁跳步）</li>
     *   <li>角色权限（R2：只有 DEV 推进；关闭仅 PM）</li>
     *   <li>数据归属（R5：只能操作分给自己的任务）</li>
     *   <li>说明必填（打回、关闭）</li>
     * </ol>
     * 全部通过后执行乐观锁更新并追加事件记录。
     *
     * @param id      任务 ID
     * @param request 状态流转参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transit(Long id, TaskStatusUpdateRequest request) {

        // 1. 加载任务
        ImprovementTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }

        // 2. 目标状态合法性校验
        TaskStatus target = TaskStatus.of(request.getStatus());
        if (target == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "非法的目标状态：" + request.getStatus());
        }

        // 3. 状态机流转校验（禁止跳步、禁止终态回流）
        TaskStatus current = TaskStatus.of(task.getStatus());
        if (current == null) {
            throw new BizException(ResultCode.CONFLICT, "任务状态异常，请联系管理员");
        }
        if (!current.canTransitTo(target.getCode())) {
            throw new BizException(ResultCode.CONFLICT,
                    "不允许的状态流转：" + current.getLabel() + " → " + target.getLabel());
        }

        // 4. 角色权限校验（R2）
        String role = UserContext.getRole();
        boolean isAdmin = RoleEnum.ADMIN.getCode().equals(role);

        if (TaskStatus.WONT_DO == target) {
            // 关闭任务属于产品决策，仅 PM 可执行
            if (!isAdmin && !RoleEnum.PM.getCode().equals(role)) {
                throw new BizException(ResultCode.FORBIDDEN, "只有产品经理可以关闭任务");
            }
        } else {
            // 推进开发状态仅 DEV 可执行，PM 不能越过 DEV 标记完成
            if (!isAdmin && !RoleEnum.DEV.getCode().equals(role)) {
                throw new BizException(ResultCode.FORBIDDEN,
                        "只有开发测试人员可以推进任务状态");
            }
        }

        // 5. 数据归属校验（R5）
        //
        // 【修复说明 · 阶段 5 权限 Review】
        // 原写法对"所有非 ADMIN 角色"统一做归属校验，于是 PM 一旦遇到
        // 已指派给某个开发的任务，关闭动作会被 403 拦下。
        // 但「决定不做」恰恰最常发生在任务已经派出去之后——
        // 这正是产品决策要叫停开发的场景，原规则把它堵死了。
        //
        // 现按角色区分：
        //   - DEV  ：只能推进分配给自己的任务（归属约束成立）
        //   - PM   ：关闭任务属于产品决策，不受负责人限制
        //   - ADMIN：全局权限，不受限制
        boolean isDev = RoleEnum.DEV.getCode().equals(role);
        if (!isAdmin && isDev && task.getAssigneeId() != null
                && !task.getAssigneeId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "该任务已指派给他人，你无权操作");
        }

        // 6. 说明必填校验
        if (TaskStatus.requireRemark(current.getCode(), target.getCode())
                && !StringUtils.hasText(request.getRemark())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该状态流转必须填写说明");
        }

        // 7. 执行更新（乐观锁：version 不匹配时更新行数为 0）
        ImprovementTask update = new ImprovementTask();
        update.setId(id);
        update.setStatus(target.getCode());
        update.setVersion(task.getVersion());

        // 未指派的任务被首次领取时，自动把当前操作人设为负责人
        //
        // 【修复说明 · 阶段 5 权限 Review】
        // 原先这里无条件把"当前操作人"设为负责人。但 ADMIN 也可以流转任务，
        // 一旦 ADMIN 操作了一个未指派任务，任务负责人就变成了 ADMIN——
        // 而 ADMIN 并不是 DEV 角色，此后任何 DEV 都会在数据归属校验处被拒
        // （"该任务已指派给他人，你无权操作"），这个任务就彻底卡死了。
        // 现改为：仅当操作人是 DEV 时才自动认领；
        // ADMIN 属于代操作/运维视角，不应改变任务的归属关系。
        if (task.getAssigneeId() == null
                && RoleEnum.DEV.getCode().equals(UserContext.getRole())) {
            update.setAssigneeId(UserContext.getUserId());
        }

        int rows = taskMapper.updateById(update);
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该任务已被他人修改，请刷新后重试");
        }

        // 8. 追加事件记录（时间线）
        String remark = StringUtils.hasText(request.getRemark())
                ? request.getRemark().trim()
                : (current.getLabel() + " → " + target.getLabel());
        appendEvent(id, current.getCode(), target.getCode(), remark);

        // 9. 任务完成时，同步关闭关联反馈，形成闭环
        if (TaskStatus.DONE == target) {
            closeLinkedFeedbacks(id);
        }

        // 10. 通知任务创建人（操作人是创建人本人时不发）
        if (task.getCreatedBy() != null
                && !task.getCreatedBy().equals(UserContext.getUserId())) {
            notificationService.send(task.getCreatedBy(),
                    NotificationType.TASK_STATUS,
                    "任务状态变更",
                    "你创建的任务「" + task.getTitle() + "」状态变更为 " + target.getLabel(),
                    "/tasks/" + id);
        }

        log.info("任务状态流转成功, id={}, {} → {}",
                id, current.getCode(), target.getCode());
    }

    /**
     * 设定或修改任务优先级（仅 PM）
     *
     * @param id      任务 ID
     * @param request 优先级参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePriority(Long id, TaskPriorityUpdateRequest request) {

        ImprovementTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }

        if (!TaskPriority.isValid(request.getPriority())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "非法的优先级：" + request.getPriority());
        }

        ImprovementTask update = new ImprovementTask();
        update.setId(id);
        update.setPriority(request.getPriority().trim().toUpperCase());
        update.setPriorityReason(request.getPriorityReason().trim());
        update.setVersion(task.getVersion());

        int rows = taskMapper.updateById(update);
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT, "该任务已被他人修改，请刷新后重试");
        }

        appendEvent(id, task.getStatus(), task.getStatus(),
                "设定优先级为 " + update.getPriority()
                        + "，理由：" + update.getPriorityReason());

        log.info("任务优先级变更成功, id={}, priority={}", id, update.getPriority());
    }

    /**
     * 指派任务负责人（仅 PM）
     *
     * @param id      任务 ID
     * @param request 指派参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, TaskAssignRequest request) {

        ImprovementTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }

        SysUser assignee = requireDevUser(request.getAssigneeId());

        ImprovementTask update = new ImprovementTask();
        update.setId(id);
        update.setAssigneeId(request.getAssigneeId());
        update.setVersion(task.getVersion());

        int rows = taskMapper.updateById(update);
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT, "该任务已被他人修改，请刷新后重试");
        }

        appendEvent(id, task.getStatus(), task.getStatus(),
                "指派给 " + assignee.getDisplayName());

        // 通知新负责人（操作人是本人时不发）
        if (!request.getAssigneeId().equals(UserContext.getUserId())) {
            notificationService.send(request.getAssigneeId(),
                    NotificationType.TASK_ASSIGNED,
                    "新任务指派",
                    UserContext.getUsername() + " 将任务「" + task.getTitle() + "」指派给你",
                    "/tasks/" + id);
        }

        log.info("任务指派成功, id={}, assigneeId={}", id, request.getAssigneeId());
    }

    // ==================== 查询 ====================

    /**
     * 分页查询任务列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<TaskVO> page(TaskQueryRequest query) {

        long pageNo = (query.getPageNo() == null || query.getPageNo() < 1)
                ? 1L : query.getPageNo();
        long pageSize = (query.getPageSize() == null || query.getPageSize() < 1)
                ? 10L : query.getPageSize();

        // 处理 assigneeId=myself 的特殊值
        Long assigneeId = resolveAssigneeId(query.getAssigneeId());

        // 模块在 issue 上而非 task 上，先查出该模块下的问题ID，再据此过滤任务
        List<Long> issueIds = resolveIssueIdsByModule(query.getModule());

        LambdaQueryWrapper<ImprovementTask> wrapper =
                new LambdaQueryWrapper<ImprovementTask>()
                        .eq(StringUtils.hasText(query.getStatus()),
                                ImprovementTask::getStatus, query.getStatus())
                        .eq(StringUtils.hasText(query.getPriority()),
                                ImprovementTask::getPriority, query.getPriority())
                        .in(issueIds != null,
                                ImprovementTask::getIssueId, issueIds)
                        .eq(assigneeId != null,
                                ImprovementTask::getAssigneeId, assigneeId)
                        .and(StringUtils.hasText(query.getKeyword()),
                                w -> w.like(ImprovementTask::getTitle, query.getKeyword())
                                        .or()
                                        .like(ImprovementTask::getRequirement, query.getKeyword())
                                        .or()
                                        .like(ImprovementTask::getTaskNo, query.getKeyword()))
                        .orderByAsc(ImprovementTask::getPriority)
                        .orderByDesc(ImprovementTask::getCreateTime);

        IPage<ImprovementTask> pageResult =
                taskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        List<ImprovementTask> records = pageResult.getRecords();
        Map<Long, String> userNames = resolveUserNames(records);
        Map<Long, String> issueNos = resolveIssueNos(records);

        return PageResult.of(pageResult,
                entity -> toVO(entity, userNames, issueNos));
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务视图对象
     */
    @Override
    public TaskVO detail(Long id) {

        ImprovementTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }

        // 详情需要准确拿到两个人名，单独查询（单条记录，无 N+1 问题）
        Map<Long, String> userNames = new HashMap<>();
        for (Long userId : List.of(task.getAssigneeId(), task.getCreatedBy())) {
            if (userId != null) {
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null) {
                    userNames.put(userId, user.getDisplayName());
                }
            }
        }

        // 回显关联问题编号（单条记录，无 N+1 问题）
        Map<Long, String> issueNos = new HashMap<>();
        if (task.getIssueId() != null) {
            Issue issue = issueMapper.selectById(task.getIssueId());
            if (issue != null) {
                issueNos.put(issue.getId(), issue.getIssueNo());
            }
        }

        return toVO(task, userNames, issueNos);
    }

    /**
     * 查询任务时间线
     *
     * @param taskId 任务 ID
     * @return 事件列表
     */
    @Override
    public List<TaskEventVO> events(Long taskId) {

        ImprovementTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已被删除");
        }

        List<TaskEvent> events = taskEventMapper.selectList(
                new LambdaQueryWrapper<TaskEvent>()
                        .eq(TaskEvent::getTaskId, taskId)
                        .orderByAsc(TaskEvent::getCreateTime)
                        .orderByAsc(TaskEvent::getId)
        );

        // 事件可能涉及多个操作人，批量查姓名
        List<Long> operatorIds = events.stream()
                .map(TaskEvent::getOperatorId)
                .distinct()
                .toList();

        Map<Long, String> names = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            sysUserMapper.selectBatchIds(operatorIds)
                    .forEach(u -> names.put(u.getId(), u.getDisplayName()));
        }

        return events.stream()
                .map(e -> TaskEventVO.builder()
                        .id(e.getId())
                        .taskId(e.getTaskId())
                        .fromStatus(e.getFromStatus())
                        .fromStatusLabel(StatusLabelUtil.taskStatusLabel(e.getFromStatus()))
                        .toStatus(e.getToStatus())
                        .toStatusLabel(StatusLabelUtil.taskStatusLabel(e.getToStatus()))
                        .operatorId(e.getOperatorId())
                        .operatorName(names.get(e.getOperatorId()))
                        .operatorRole(e.getOperatorRole())
                        .remark(e.getRemark())
                        .createTime(e.getCreateTime())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 去除首尾空白，空串归一化为 null
     *
     * <p>数据库里存空字符串和存 NULL 在业务上是两回事：
     * 空串会被前端当成"有值但内容是空的"渲染出一个空的说明区域，
     * 而 NULL 才是"确实没填"。统一归一化为 NULL，避免这种歧义。
     *
     * @param raw 原始字符串
     * @return 去除空白后的字符串；若为 null 或全空白则返回 null
     */
    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 生成任务编号
     *
     * <p>格式：TK-0001，序号取当前最大 ID + 1
     *
     * @return 任务编号
     */
    private String generateTaskNo() {
        ImprovementTask last = taskMapper.selectOne(
                new LambdaQueryWrapper<ImprovementTask>()
                        .select(ImprovementTask::getId)
                        .orderByDesc(ImprovementTask::getId)
                        .last("LIMIT 1")
        );
        long seq = (last == null || last.getId() == null) ? 1L : last.getId() + 1L;
        return "TK-" + String.format("%04d", seq);
    }

    /**
     * 追加任务事件（时间线节点）
     *
     * <p><b>为什么连"创建"也要记一条事件</b>：
     * 时间线必须能回答"这个任务从哪来"，
     * 否则打开事件列表只有几条状态变更，缺少起点。
     *
     * @param taskId   任务 ID
     * @param fromStatus 原状态，创建时为 null
     * @param toStatus   新状态
     * @param remark     说明
     */
    private void appendEvent(Long taskId, String fromStatus,
                             String toStatus, String remark) {
        TaskEvent event = new TaskEvent();
        event.setTaskId(taskId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorId(UserContext.getUserId());
        // 角色存快照：人员转岗后，历史记录仍反映当时的真实身份
        event.setOperatorRole(UserContext.getRole());
        event.setRemark(remark);
        taskEventMapper.insert(event);
    }

    /**
     * 任务完成时关闭关联反馈
     *
     * <p>这一步让整条链路形成闭环：
     * 反馈 → 问题 → 任务 → 完成 → 反馈关闭。
     * 客服在反馈列表里看到状态变为"已关闭"，
     * 就知道客户反映的问题已经解决了。
     *
     * @param taskId 任务 ID
     */
    private void closeLinkedFeedbacks(Long taskId) {

        ImprovementTask task = taskMapper.selectById(taskId);

        List<FeedbackIssueLink> links = feedbackIssueLinkMapper.selectList(
                new LambdaQueryWrapper<FeedbackIssueLink>()
                        .eq(FeedbackIssueLink::getIssueId, task.getIssueId())
        );

        for (FeedbackIssueLink link : links) {
            Feedback feedback = feedbackMapper.selectById(link.getFeedbackId());
            if (feedback != null
                    && FeedbackStatus.LINKED.getCode().equals(feedback.getStatus())) {
                Feedback update = new Feedback();
                update.setId(feedback.getId());
                update.setStatus(FeedbackStatus.CLOSED.getCode());
                update.setVersion(feedback.getVersion());
                feedbackMapper.updateById(update);
            }
        }
    }

    /**
     * 按功能模块查询问题 ID 列表
     *
     * <p><b>为什么要绕这一圈</b>：
     * 模块是问题（issue）的属性，任务（task）表上没有冗余这个字段。
     * 直接联表 SQL 当然也行，但会破坏 Service 层的可测试性，
     * 且 MyBatis-Plus 的联表能力有限。
     * 先查 ID 再 IN 过滤，在数据量千级以内完全够用，代码也更直白。
     *
     * @param module 功能模块代码，为空表示不筛选
     * @return 问题ID列表；不筛选时返回 null；筛选但无结果时返回 {-1}
     */
    private List<Long> resolveIssueIdsByModule(String module) {

        if (!StringUtils.hasText(module)) {
            return null;
        }

        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId)
                        .eq(Issue::getModule, FeedbackModule.of(module).getCode())
        );

        if (issues == null || issues.isEmpty()) {
            // 返回一个不可能存在的ID，确保查询结果为零行。
            // 不能直接返回空列表——MyBatis-Plus 对空集合会渲染出 IN () 这样的非法 SQL。
            return List.of(-1L);
        }

        return issues.stream().map(Issue::getId).toList();
    }

    /**
     * 解析负责人筛选条件
     *
     * <p>支持传 myself，表示只看分配给当前登录用户的任务。
     * 这样 DEV 打开工作台时，默认就能看到"我要做什么"。
     *
     * @param raw 原始参数值
     * @return 负责人ID，无筛选时返回 null
     */
    private Long resolveAssigneeId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        if ("myself".equalsIgnoreCase(raw.trim())) {
            return UserContext.getUserId();
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // 非法数字不抛异常，忽略该筛选条件即可——
        // 筛选条件本身不是关键业务数据，严格报错反而影响使用
            return null;
        }
    }

    /**
     * 批量查询相关人员姓名
     *
     * @param tasks 任务列表
     * @return 用户ID → 姓名的映射
     */
    /**
     * 批量解析任务关联问题的编号
     *
     * <p><b>修复背景（阶段 5 契约核对发现）</b>：
     * {@code TaskVO} 里声明了 {@code issueNo} 字段、前端任务卡片也引用了它，
     * 但后端 {@code toVO()} 从来没有给它赋过值 ——
     * 结果是前端永远显示空白，而接口返回 200、前端也不报错，
     * 属于"看起来一切正常但就是少一块"的静默缺陷。
     * 这类问题靠肉眼看代码极难发现，正是自动化契约核对的价值所在。
     *
     * <p>同样采用批量查询避免 N+1：一次 {@code selectBatchIds} 拿回全部。
     *
     * @param tasks 当前页任务列表
     * @return 问题ID → 问题编号 映射
     */
    private Map<Long, String> resolveIssueNos(List<ImprovementTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (ImprovementTask t : tasks) {
            if (t.getIssueId() != null) {
                ids.add(t.getIssueId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> nos = new HashMap<>();
        issueMapper.selectBatchIds(new ArrayList<>(ids))
                .forEach(i -> nos.put(i.getId(), i.getIssueNo()));
        return nos;
    }

    private Map<Long, String> resolveUserNames(List<ImprovementTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (ImprovementTask t : tasks) {
            if (t.getAssigneeId() != null) {
                ids.add(t.getAssigneeId());
            }
            if (t.getCreatedBy() != null) {
                ids.add(t.getCreatedBy());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new HashMap<>();
        sysUserMapper.selectBatchIds(new ArrayList<>(ids))
                .forEach(u -> names.put(u.getId(), u.getDisplayName()));
        return names;
    }

    /**
     * 校验并返回开发测试人员
     *
     * <p>为什么在服务端校验角色而不信前端：
     * 前端只负责从 DEV 列表里选，绕过页面直接调接口就能传任意 ID。
     * 把任务指派给客服账号会导致他在工作台上永远看不到这个任务。
     *
     * @param userId 待校验用户 ID
     * @return 用户实体
     */
    private SysUser requireDevUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "指派的负责人不存在或已禁用");
        }
        if (!RoleEnum.DEV.getCode().equals(user.getRole())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "只能将任务指派给开发测试人员");
        }
        return user;
    }

    /**
     * 实体转 VO
     *
     * @param entity    任务实体
     * @param userNames 用户ID → 姓名映射
     * @param issueNos  问题ID → 问题编号映射（用于回显"本任务来自哪个问题"）
     * @return 任务视图对象
     */
    private TaskVO toVO(ImprovementTask entity,
                        Map<Long, String> userNames,
                        Map<Long, String> issueNos) {

        TaskStatus status = TaskStatus.of(entity.getStatus());

        return TaskVO.builder()
                .id(entity.getId())
                .taskNo(entity.getTaskNo())
                .issueId(entity.getIssueId())
                // 修复：此前该字段声明了却从未赋值，前端任务卡片上的
                // "关联问题编号"会一直是空白。这里从批量查询结果中回显。
                .issueNo(entity.getIssueId() == null
                        ? null : issueNos.get(entity.getIssueId()))
                .title(entity.getTitle())
                .requirement(entity.getRequirement())
                .acceptanceCriteria(entity.getAcceptanceCriteria())
                .priority(entity.getPriority())
                .priorityLabel(TaskPriority.of(entity.getPriority()).getLabel())
                .priorityReason(entity.getPriorityReason())
                .aiSuggestedPriority(entity.getAiSuggestedPriority())
                .aiEvidence(entity.getAiEvidence())
                .assigneeId(entity.getAssigneeId())
                .assigneeName(entity.getAssigneeId() == null
                        ? null : userNames.get(entity.getAssigneeId()))
                .status(entity.getStatus())
                .statusLabel(status == null
                        ? entity.getStatus() : status.getLabel())
                // 把状态机的下一步直接告诉前端，避免前端维护第二份规则
                .nextStatuses(status == null
                        ? List.of() : List.copyOf(status.getNextStatuses()))
                .createdByName(entity.getCreatedBy() == null
                        ? null : userNames.get(entity.getCreatedBy()))
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
