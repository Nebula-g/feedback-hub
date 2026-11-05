package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问题视图对象
 *
 * <p><b>本 VO 最关键的两个字段：aiDraftContent 与 finalContent</b>
 * <ul>
 *   <li>aiDraftContent —— AI 原话，确认后永不覆盖</li>
 *   <li>finalContent   —— PM 定稿内容</li>
 * </ul>
 * 前端把这两个字段并排展示（或做 diff），
 * 就能直观证明"AI 建议了什么、人最终改成了什么"。
 * 这是验收场景 5 的直接证据。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "问题信息")
public class IssueVO {

    /** 主键 ID */
    @Schema(description = "问题ID", example = "1")
    private Long id;

    /** 问题编号 */
    @Schema(description = "问题编号", example = "IS-0001")
    private String issueNo;

    /** 问题标题 */
    @Schema(description = "问题标题", example = "发票上传大文件时必然失败")
    private String title;

    /** 问题描述 */
    @Schema(description = "问题描述")
    private String description;

    /** 功能模块代码 */
    @Schema(description = "功能模块代码", example = "INVOICE_UPLOAD")
    private String module;

    /** 功能模块中文名 */
    @Schema(description = "功能模块中文名", example = "发票上传")
    private String moduleLabel;

    /**
     * AI 生成的需求草稿（原话，永不覆盖）
     *
     * <p>手工创建的问题该字段为空。
     */
    @Schema(description = "AI生成的需求草稿原文")
    private String aiDraftContent;

    /** PM 定稿的需求说明 */
    @Schema(description = "PM定稿的需求说明")
    private String finalContent;

    /** 验收条件 */
    @Schema(description = "验收条件")
    private String acceptanceCriteria;

    /** 来源类型：AI / MANUAL */
    @Schema(description = "来源类型", example = "MANUAL")
    private String sourceType;

    /**
     * AI 建议与人工定稿是否存在差异
     *
     * <p>这个字段是专门给验收演示用的：
     * 只要它为 true，就说明 PM 修改了 AI 的建议，
     * 「AI 生成内容与人工确认结果可区分」这一条自动成立。
     */
    @Schema(description = "AI建议与人工定稿是否存在差异")
    private Boolean modifiedFromAi;

    /** 确认人 ID */
    @Schema(description = "确认人ID", example = "2")
    private Long confirmedBy;

    /** 确认人姓名 */
    @Schema(description = "确认人姓名", example = "李产品")
    private String confirmedByName;

    /** 确认时间 */
    @Schema(description = "确认时间")
    private LocalDateTime confirmTime;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 关联的反馈 ID 列表 */
    @Schema(description = "关联反馈ID列表")
    private List<Long> feedbackIds;
}
