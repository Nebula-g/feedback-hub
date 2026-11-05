package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务状态流转请求参数
 *
 * <p><b>状态机规则（对应需求文档 4.1 节）</b>：
 * <pre>
 *   TODO → IN_PROGRESS → PENDING_VERIFY → DONE
 *     └──────────────→ WONT_DO
 * </pre>
 * 非法的跳转（如 TODO → DONE）会被服务端拒绝并返回 409。
 *
 * <p>必须填写说明的跳转：
 * <ul>
 *   <li>PENDING_VERIFY → IN_PROGRESS：验证不通过，需写明打回原因</li>
 *   <li>任意状态 → WONT_DO：需写明关闭理由</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "任务状态流转请求")
public class TaskStatusUpdateRequest {

    /** 目标状态 */
    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态：IN_PROGRESS / PENDING_VERIFY / DONE / WONT_DO",
            example = "IN_PROGRESS")
    private String status;

    /** 流转说明：打回或关闭时必填 */
    @Schema(description = "流转说明（打回或关闭时必填）",
            example = "验证不通过：大文件上传仍超时，请检查分片逻辑")
    private String remark;
}
