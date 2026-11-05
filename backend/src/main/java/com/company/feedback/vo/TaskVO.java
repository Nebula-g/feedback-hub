package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 改进任务视图对象
 *
 * <p><b>nextStatuses 字段的意义</b>：
 * 服务端把"当前状态允许流转到哪些状态"直接返回给前端，
 * 前端据此决定显示哪些操作按钮。
 *
 * <p>这样做的好处：状态机规则只有服务端一处定义，
 * 前端不需要维护第二份规则副本，
 * 也就不会出现"前端显示按钮、后端却拒绝"的矛盾。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "改进任务信息")
public class TaskVO {

    /** 主键 ID */
    @Schema(description = "任务ID", example = "1")
    private Long id;

    /** 任务编号 */
    @Schema(description = "任务编号", example = "TK-0001")
    private String taskNo;

    /** 关联问题 ID */
    @Schema(description = "关联问题ID", example = "1")
    private Long issueId;

    /** 关联问题编号 */
    @Schema(description = "关联问题编号", example = "IS-0001")
    private String issueNo;

    /** 任务标题 */
    @Schema(description = "任务标题", example = "优化发票上传组件的文件大小限制")
    private String title;

    /** 需求说明 */
    @Schema(description = "需求说明")
    private String requirement;

    /** 验收条件 */
    @Schema(description = "验收条件")
    private String acceptanceCriteria;

    /** 优先级代码（PM 设定） */
    @Schema(description = "优先级", example = "P0")
    private String priority;

    /** 优先级中文名 */
    @Schema(description = "优先级中文名", example = "P0 - 阻塞主流程")
    private String priorityLabel;

    /** PM 填写的定级理由 */
    @Schema(description = "定级理由")
    private String priorityReason;

    /**
     * AI 建议的优先级（仅供参考）
     *
     * <p>注意：该字段<b>永远不会</b>自动写入 priority，
     * 仅作为 PM 决策时的参考信息展示。
     */
    @Schema(description = "AI建议优先级，仅供参考", example = "P1")
    private String aiSuggestedPriority;

    /**
     * AI 提供的客观统计依据
     *
     * <p>内容为可核验的事实（关联反馈条数、涉及客户数、是否阻塞主流程），
     * 而非主观判断。
     */
    @Schema(description = "AI提供的客观统计依据")
    private String aiEvidence;

    /** 负责人 ID */
    @Schema(description = "负责人ID", example = "3")
    private Long assigneeId;

    /** 负责人姓名 */
    @Schema(description = "负责人姓名", example = "王开发")
    private String assigneeName;

    /** 状态代码 */
    @Schema(description = "状态", example = "TODO")
    private String status;

    /** 状态中文名 */
    @Schema(description = "状态中文名", example = "待开发")
    private String statusLabel;

    /** 当前状态允许流转到的目标状态列表 */
    @Schema(description = "允许的下一步状态", example = "[\"IN_PROGRESS\", \"WONT_DO\"]")
    private List<String> nextStatuses;

    /** 创建人姓名 */
    @Schema(description = "创建人姓名", example = "李产品")
    private String createdByName;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
