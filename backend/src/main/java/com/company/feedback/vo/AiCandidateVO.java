package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 候选问题卡片视图对象
 *
 * <p><b>本 VO 对应「草稿」这一身份，与 IssueVO 有本质区别</b>：
 * <ul>
 *   <li>AiCandidateVO —— AI 的建议，未经确认，不产生任何业务事实</li>
 *   <li>IssueVO       —— PM 确认后的正式问题，参与统计与任务流转</li>
 * </ul>
 * 前端必须让这两者在视觉上可区分（本项目的候选卡片统一带"未确认草稿"标签）。
 *
 * <p><b>没有 priority 字段是刻意的</b>：
 * AI 只输出客观依据（{@code relatedFeedbackIds} 条数、{@code module}、{@code aiConfidence}），
 * 定级权始终在 PM 手里。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "AI候选问题卡片")
public class AiCandidateVO {

    /** 候选卡片 ID */
    @Schema(description = "候选卡片ID", example = "1")
    private Long id;

    /** 生成批次号 */
    @Schema(description = "批次号", example = "B20261027153000")
    private String batchNo;

    /** 候选问题标题 */
    @Schema(description = "候选问题标题")
    private String title;

    /** 问题现象 */
    @Schema(description = "问题现象")
    private String phenomenon;

    /** 用户诉求 */
    @Schema(description = "用户诉求")
    private String userDemand;

    /** 功能模块代码 */
    @Schema(description = "功能模块代码", example = "INVOICE_UPLOAD")
    private String module;

    /** 功能模块中文名 */
    @Schema(description = "功能模块中文名", example = "发票上传")
    private String moduleLabel;

    /**
     * AI 置信度
     *
     * <p>仅用于列表排序与提示，<b>不触发任何自动决策</b>。
     */
    @Schema(description = "AI置信度", example = "0.86")
    private BigDecimal aiConfidence;

    /** AI 给出该归类的理由 */
    @Schema(description = "归类理由")
    private String aiReason;

    /**
     * 关联的反馈 ID 列表
     *
     * <p>这是"可回查"的入口：每条结论都能点回原始反馈。
     */
    @Schema(description = "关联反馈ID列表")
    private List<Long> relatedFeedbackIds;

    /** 状态代码 */
    @Schema(description = "状态", example = "DRAFT")
    private String status;

    /** 状态中文名 */
    @Schema(description = "状态中文名", example = "待处理")
    private String statusLabel;

    /** 处理人 ID */
    @Schema(description = "处理人ID")
    private Long handledBy;

    /** 处理人姓名 */
    @Schema(description = "处理人姓名", example = "张PM")
    private String handledByName;

    /** 处理说明 */
    @Schema(description = "处理说明")
    private String handleRemark;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    /** 确认后生成的正式问题 ID（未确认为空） */
    @Schema(description = "生成的正式问题ID")
    private Long generatedIssueId;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
