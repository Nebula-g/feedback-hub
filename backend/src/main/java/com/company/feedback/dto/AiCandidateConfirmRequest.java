package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PM 确认候选卡片请求
 *
 * <p><b>本请求承载了本项目最重要的一次「人机分界」</b>：
 * AI 的产出（候选卡片内容）在这里被读取一次，
 * PM 的定稿（title / description / finalContent / acceptanceCriteria）被提交，
 * 后端把两份内容同时写入 issue 的不同字段：
 * <pre>
 *   issue.ai_draft_content = AI 原话（永不覆盖）
 *   issue.final_content    = PM 定稿
 * </pre>
 * 两者只要不一致，候选卡片状态就记为 MODIFIED，
 * 这既是"人改了 AI"的证据，也是验收场景 5 的直接依据。
 *
 * <p>注意：<b>本请求没有 priority 字段</b>。
 * 优先级由 PM 后续在任务创建时另行设定，AI 全程不参与定级。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "确认候选卡片请求")
public class AiCandidateConfirmRequest {

    /** PM 确认（或修改后的）问题标题 */
    @NotBlank(message = "问题标题不能为空")
    @Size(max = 200, message = "问题标题不能超过200字")
    @Schema(description = "问题标题")
    private String title;

    /** PM 确认（或修改后的）问题描述 */
    @Schema(description = "问题描述")
    private String description;

    /** PM 定稿的需求说明 */
    @Schema(description = "需求说明（PM定稿）")
    private String finalContent;

    /** PM 填写的验收条件 */
    @Schema(description = "验收条件")
    private String acceptanceCriteria;
}
