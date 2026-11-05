package com.company.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 起草的需求说明结果（AI 作用点 C：DRAFT）
 *
 * <p><b>关键约束：本结果不会直接覆盖 issue.final_content</b>。
 * 它只会作为草稿返回给前端，由 PM 编辑确认后才会写入正式字段。
 * 未经确认的内容始终停留在 ai_candidate_issue / 前端表单里，
 * 不进入任何正式业务表。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftResult {

    /** 需求说明草稿 */
    private String draftContent;

    /** 验收条件草稿 */
    private String acceptanceCriteria;

    /**
     * AI 返回原文
     *
     * <p>与 {@link AiClusterResult#getRawOutput()} 同理，
     * 用于原样落库、事后复盘。
     */
    private String rawOutput;
}
