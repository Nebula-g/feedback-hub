package com.company.feedback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务事件视图对象（时间线节点）
 *
 * <p>每一次状态变更都会追加一条记录，构成
 * "谁在什么时候、以什么身份、把任务从什么状态改成了什么状态" 的完整时间线。
 *
 * <p><b>为什么 operatorRole 存快照</b>：
 * 若只存 operatorId 再关联查询当前角色，
 * 人员转岗后历史记录会全部"变样"，失真。存快照保住的是历史真相。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "任务事件（时间线节点）")
public class TaskEventVO {

    /** 事件 ID */
    @Schema(description = "事件ID", example = "1")
    private Long id;

    /** 任务 ID */
    @Schema(description = "任务ID", example = "1")
    private Long taskId;

    /** 原状态代码 */
    @Schema(description = "原状态代码", example = "TODO")
    private String fromStatus;

    /** 原状态中文名 */
    @Schema(description = "原状态中文名", example = "待开发")
    private String fromStatusLabel;

    /** 新状态代码 */
    @Schema(description = "新状态代码", example = "IN_PROGRESS")
    private String toStatus;

    /** 新状态中文名 */
    @Schema(description = "新状态中文名", example = "开发中")
    private String toStatusLabel;

    /** 操作人 ID */
    @Schema(description = "操作人ID", example = "3")
    private Long operatorId;

    /** 操作人姓名 */
    @Schema(description = "操作人姓名", example = "王开发")
    private String operatorName;

    /** 操作人角色快照 */
    @Schema(description = "操作人角色快照", example = "DEV")
    private String operatorRole;

    /** 处理说明 */
    @Schema(description = "处理说明")
    private String remark;

    /** 发生时间 */
    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
