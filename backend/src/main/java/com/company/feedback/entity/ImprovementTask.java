package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 改进任务实体【核心业务对象 · 正式】
 *
 * <p>对应表：improvement_task
 *
 * <p><b>身份定位</b>：由已确认问题（issue）转化而来的、可执行、可验收的开发任务。
 * 一个 issue 可以转化出多个 task（分期实现），但首版 MVP 限定 1:1。
 *
 * <p><b>本表最重要的设计：priority 与 ai_suggested_priority 分离</b>
 * <pre>
 *   priority              = PM 手选的最终优先级（唯一生效值）
 *   ai_suggested_priority = AI 的建议值（仅供参考，绝不自动写入 priority）
 *   ai_evidence           = AI 给出的客观统计依据（反馈条数、涉及客户数等）
 * </pre>
 *
 * <p>这条设计直接对应选题文档的 AI 约束 A4：
 * 「AI 不能根据用户表达情绪的强烈程度自动决定优先级」。
 *
 * <p>AI 只输出<b>可统计的客观事实</b>：关联了几条反馈、涉及几个客户、
 * 是否阻塞主流程、涉及哪个模块。
 * 定级这个决定权 100% 在人手里，且必须填写 priority_reason 说明理由。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("improvement_task")
public class ImprovementTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 任务编号，如 TK-0001
     */
    @TableField("task_no")
    private String taskNo;

    /**
     * 关联问题 ID
     */
    @TableField("issue_id")
    private Long issueId;

    /**
     * 任务标题
     */
    @TableField("title")
    private String title;

    /**
     * 需求说明（由 PM 定稿内容带入）
     */
    @TableField("requirement")
    private String requirement;

    /**
     * 验收条件
     */
    @TableField("acceptance_criteria")
    private String acceptanceCriteria;

    /**
     * 优先级 P0 / P1 / P2
     *
     * <p><b>本字段完全由 PM 手动设定，AI 无写入权限</b>。
     */
    @TableField("priority")
    private String priority;

    /**
     * PM 填写的定级理由（必填）
     *
     * <p>强制填写的理由：让每一次定级都有据可查，
     * 避免"拍脑袋定 P0"却没有解释的情况。
     */
    @TableField("priority_reason")
    private String priorityReason;

    /**
     * AI 建议优先级，仅供参考
     */
    @TableField("ai_suggested_priority")
    private String aiSuggestedPriority;

    /**
     * AI 提供的客观统计依据（JSON 字符串）
     * <pre>
     * {"feedbackCount":7,"customerCount":5,"blocking":true,"module":"INVOICE_UPLOAD"}
     * </pre>
     */
    @TableField("ai_evidence")
    private String aiEvidence;

    /**
     * 负责人（DEV）
     */
    @TableField("assignee_id")
    private Long assigneeId;

    /**
     * 任务状态：TODO / IN_PROGRESS / PENDING_VERIFY / DONE / WONT_DO
     *
     * <p>状态流转规则见 {@link com.company.feedback.enums.TaskStatus}
     */
    @TableField("status")
    private String status;

    /**
     * 创建人（PM）
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 逻辑删除：0-正常 1-已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 乐观锁版本号：防止两人同时修改任务状态造成覆盖
     */
    @Version
    @TableField("version")
    private Integer version;
}
