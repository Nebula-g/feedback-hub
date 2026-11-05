package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务优先级设定请求参数
 *
 * <p><b>只有产品经理可以调用</b>（权限规则 R1）。
 *
 * <p>为什么要求必填定级理由：
 * 优先级是一种判断而非事实，必须留下"为什么这么定"的记录。
 * 否则三个月后回看，没人说得清当初为什么 P0。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "任务优先级设定请求")
public class TaskPriorityUpdateRequest {

    /** 目标优先级：P0 / P1 / P2 */
    @NotBlank(message = "优先级不能为空")
    @Schema(description = "优先级", example = "P0")
    private String priority;

    /** 定级理由（必填） */
    @NotBlank(message = "定级理由不能为空")
    @Schema(description = "定级理由",
            example = "7家客户反馈，且报销单无法提交属于主流程阻塞")
    private String priorityReason;
}
