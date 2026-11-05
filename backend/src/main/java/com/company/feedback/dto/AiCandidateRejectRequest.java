package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PM 驳回候选卡片请求
 *
 * <p><b>为什么驳回原因必填</b>：
 * 驳回是一个"否定 AI 结论"的动作，必须留下理由。
 * 否则事后无法区分"AI 确实错了"和"PM 当时随手点了驳回"。
 * 这个理由会同时写入 ai_candidate_issue.handle_remark，
 * 与 AI 的原稿放在一起，构成完整的一问一答。
 *
 * <p>驳回后，卡片关联的所有反馈会回到"未归类"状态，
 * 等待下一轮生成时被重新归纳，不会丢数据。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "驳回候选卡片请求")
public class AiCandidateRejectRequest {

    /** 驳回原因（必填） */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 1000, message = "驳回原因不能超过1000字")
    @Schema(description = "驳回原因",
            example = "现象描述过于笼统，无法定位到具体功能点")
    private String reason;
}
