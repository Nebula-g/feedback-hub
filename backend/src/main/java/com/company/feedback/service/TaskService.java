package com.company.feedback.service;

import com.company.feedback.common.PageResult;
import com.company.feedback.dto.TaskAssignRequest;
import com.company.feedback.dto.TaskCreateRequest;
import com.company.feedback.dto.TaskPriorityUpdateRequest;
import com.company.feedback.dto.TaskQueryRequest;
import com.company.feedback.dto.TaskStatusUpdateRequest;
import com.company.feedback.vo.TaskEventVO;
import com.company.feedback.vo.TaskVO;

import java.util.List;

/**
 * 改进任务服务接口
 *
 * <p><b>核心业务规则（对应需求文档第 4 节）</b>：
 * <ul>
 *   <li>R1 —— 只有 PM 能设定和修改优先级，AI 只提供客观依据与建议分</li>
 *   <li>R2 —— 只有 DEV 能推进任务状态；PM 只能创建任务和关闭任务</li>
 *   <li>R5 —— 任务负责人只能操作分配给自己的任务</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
public interface TaskService {

    /**
     * PM 创建改进任务
     *
     * <p>任务必须由已确认问题转化而来，不能凭空创建。
     * 这保证了「问题 → 任务」链条的完整性，
     * 每个任务都能回溯到它解决了什么问题。
     *
     * @param request 创建参数
     * @return 新建任务的 ID
     */
    Long create(TaskCreateRequest request);

    /**
     * 分页查询任务列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TaskVO> page(TaskQueryRequest query);

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务视图对象
     */
    TaskVO detail(Long id);

    /**
     * 推进任务状态
     *
     * <p>服务端会依次校验：
     * 状态机规则 → 角色权限 → 数据归属 → 说明必填 → 乐观锁并发。
     * 任何一环不满足都会中断并抛出业务异常。
     *
     * @param id      任务 ID
     * @param request 状态流转参数
     */
    void transit(Long id, TaskStatusUpdateRequest request);

    /**
     * 设定或修改任务优先级（仅 PM）
     *
     * <p>本方法是「AI 不自动决定优先级」这条约束的执行点：
     * priority 字段只能由本方法写入，AI 的建议只会落在
     * aiSuggestedPriority 字段上。
     *
     * @param id      任务 ID
     * @param request 优先级参数
     */
    void updatePriority(Long id, TaskPriorityUpdateRequest request);

    /**
     * 指派任务负责人（仅 PM）
     *
     * @param id      任务 ID
     * @param request 指派参数
     */
    void assign(Long id, TaskAssignRequest request);

    /**
     * 查询任务时间线
     *
     * @param taskId 任务 ID
     * @return 事件列表（按时间正序）
     */
    List<TaskEventVO> events(Long taskId);
}
