package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.dto.TaskAssignRequest;
import com.company.feedback.dto.TaskCreateRequest;
import com.company.feedback.dto.TaskPriorityUpdateRequest;
import com.company.feedback.dto.TaskQueryRequest;
import com.company.feedback.dto.TaskStatusUpdateRequest;
import com.company.feedback.service.TaskService;
import com.company.feedback.vo.TaskEventVO;
import com.company.feedback.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 改进任务控制器
 *
 * <p><b>权限矩阵（对应需求文档第 4 节 R1 / R2 / R5）</b>：
 * <pre>
 *   接口            CS    PM    DEV   ADMIN
 *   创建任务        ✗     ✓     ✗     ✗
 *   查询列表        ✓     ✓     ✓     ✓
 *   查询详情        ✓     ✓     ✓     ✓
 *   推进状态        ✗     ✗     ✓     ✗
 *   设定优先级      ✗     ✓     ✗     ✗
 *   指派负责人      ✗     ✓     ✗     ✗
 *   查询时间线      ✓     ✓     ✓     ✓
 * </pre>
 *
 * <p><b>为什么推进状态只允许 DEV</b>：
 * 任务的进展只有实际开发的人知道，PM 不该代替开发点击"已完成"。
 * 但 PM 拥有创建权和定级权，形成"PM 决定做什么、DEV 决定做到哪"的分工。
 *
 * <p><b>关于 409 冲突</b>：
 * 状态机非法跳转会返回 {@code 409 TASK_ILLEGAL_TRANSITION}，
 * 乐观锁并发冲突会返回 {@code 409 CONFLICT}。
 * 前端需针对 409 展示冲突态 UI，而不是笼统提示"操作失败"。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "改进任务", description = "任务的创建、状态流转、优先级设定与时间线")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * PM 创建改进任务
     *
     * <p>任务必须由已确认问题转化而来。
     * 这保证了「反馈 → 问题 → 任务」链条完整，
     * 每个任务都能回溯到它解决的是哪批用户的什么问题。
     *
     * @param request 创建参数
     * @return 新建任务的 ID
     */
    @Operation(summary = "创建改进任务",
            description = "仅 PM；必须由已确认的 issue 转化，优先级与理由必填")
    @PreAuthorize("hasRole('PM')")
    @PostMapping
    public R<Map<String, Long>> create(@Valid @RequestBody TaskCreateRequest request) {
        return R.ok(Map.of("id", taskService.create(request)));
    }

    /**
     * 分页查询任务列表
     *
     * <p>支持 assigneeId=myself 快捷筛选，
     * 使 DEV 打开工作台时默认看到"分配给我的任务"。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "查询任务列表",
            description = "支持状态、优先级、模块、负责人筛选；负责人传 myself 表示只看自己的")
    @GetMapping
    public R<?> page(@Valid TaskQueryRequest query) {
        return R.ok(taskService.page(query));
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Operation(summary = "查询任务详情", description = "含 AI 建议优先级与客观依据")
    @GetMapping("/{id}")
    public R<TaskVO> detail(@PathVariable Long id) {
        return R.ok(taskService.detail(id));
    }

    /**
     * 推进任务状态
     *
     * <p>服务端按顺序校验五道关：
     * 状态机规则 → 角色权限 → 数据归属 → 说明必填 → 乐观锁。
     * 任何一环不满足都会中断并返回明确错误码。
     *
     * @param id      任务 ID
     * @param request 流转参数
     * @return 操作结果
     */
    @Operation(summary = "推进任务状态",
            description = "DEV 推进开发状态、PM 关闭任务（WONT_DO）；非法跳转返回 409；"
                    + "打回或关闭时 remark 必填")
    // 【修复说明 · 阶段 5 权限 Review】
    // 这里原先写的是 hasRole('DEV')，导致 PM 在注解层就被挡掉（403），
    // 根本进不到 Service；而 Service 里明明写了"只有 PM 可以关闭任务"的分支，
    // 于是那段逻辑成了永远执行不到的死代码，
    // 「PM 决定不做」这条状态机设计实际上不可用。
    // 现放宽为 DEV 与 PM 都可进入，由 Service 内部的角色分支做精确约束：
    //   - DEV：只能推进开发状态（IN_PROGRESS / PENDING_VERIFY / DONE）
    //   - PM ：只能执行 WONT_DO（关闭）
    @PreAuthorize("hasAnyRole('DEV', 'PM')")
    @PutMapping("/{id}/status")
    public R<Void> transit(@PathVariable Long id,
                           @Valid @RequestBody TaskStatusUpdateRequest request) {
        taskService.transit(id, request);
        return R.ok();
    }

    /**
     * 设定或修改优先级
     *
     * <p><b>这是「AI 不能自动决定优先级」这条约束的执行点</b>。
     * 本接口是 priority 字段的唯一写入口，只有 PM 能调用。
     * AI 的建议值只会落在 ai_suggested_priority 字段，不会被自动采纳。
     *
     * @param id      任务 ID
     * @param request 优先级参数
     * @return 操作结果
     */
    @Operation(summary = "设定或修改优先级",
            description = "仅 PM；AI 建议值仅供参考，不会自动写入")
    @PreAuthorize("hasRole('PM')")
    @PutMapping("/{id}/priority")
    public R<Void> updatePriority(@PathVariable Long id,
                                  @Valid @RequestBody TaskPriorityUpdateRequest request) {
        taskService.updatePriority(id, request);
        return R.ok();
    }

    /**
     * 指派负责人
     *
     * @param id      任务 ID
     * @param request 指派参数
     * @return 操作结果
     */
    @Operation(summary = "指派负责人", description = "仅 PM；必须指派给 DEV 角色的用户")
    @PreAuthorize("hasRole('PM')")
    @PutMapping("/{id}/assignee")
    public R<Void> assign(@PathVariable Long id,
                          @Valid @RequestBody TaskAssignRequest request) {
        taskService.assign(id, request);
        return R.ok();
    }

    /**
     * 查询任务时间线
     *
     * <p>时间线记录了任务从创建到完成的每一次状态变更、
     * 每一次优先级调整和每一次改派，是不可变的审计证据。
     *
     * @param id 任务 ID
     * @return 事件列表
     */
    @Operation(summary = "查询任务时间线", description = "按时间正序返回全部流转记录")
    @GetMapping("/{id}/events")
    public R<List<TaskEventVO>> events(@PathVariable Long id) {
        return R.ok(taskService.events(id));
    }
}
