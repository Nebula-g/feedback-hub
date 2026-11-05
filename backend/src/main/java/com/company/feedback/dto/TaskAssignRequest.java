package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务指派请求参数
 *
 * <p>由 PM 将任务指派给某位开发/测试人员。
 *
 * <p><b>为什么指派与创建分开</b>：
 * 实际工作中，PM 建立任务时往往还没定由谁来做，
 * 需要等技术评审后才能指派。合并成一个接口会迫使 PM 在创建时就填负责人，
 * 与现实流程不符。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Schema(description = "任务指派请求")
public class TaskAssignRequest {

    /** 负责人 ID（必须是 DEV 角色） */
    @NotNull(message = "负责人不能为空")
    @Schema(description = "负责人ID", example = "3")
    private Long assigneeId;
}
