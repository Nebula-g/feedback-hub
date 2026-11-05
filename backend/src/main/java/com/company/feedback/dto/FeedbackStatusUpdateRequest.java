package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 反馈状态变更请求参数
 *
 * <p><b>重要：本接口只允许修改 status 字段</b>。
 * 反馈原文（content）不提供任何修改入口，这是本项目的核心业务规则。
 *
 * <p>状态流转规则见 {@link com.company.feedback.enums.FeedbackStatus}。
 * 例如 NEW 只能流转到 LINKED 或 IGNORED，
 * 不能一步跳到 CLOSED。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "反馈状态变更请求")
public class FeedbackStatusUpdateRequest {

    /** 目标状态 */
    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态：NEW / LINKED / CLOSED / IGNORED",
            example = "LINKED")
    private String status;

    /** 变更说明：置为 IGNORED 时必填 */
    @Schema(description = "变更说明（置为无效时必填）",
            example = "表述不清，已回访确认为用户误操作")
    private String remark;
}
