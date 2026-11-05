package com.company.feedback.enums;

import lombok.Getter;

/**
 * 站内通知类型枚举
 *
 * <p>与 notification.type 字段一一对应。
 * 前端按类型渲染不同的图标与主题色。
 *
 * @author 反馈改进助手项目组
 */
@Getter
public enum NotificationType {

    /** 任务被指派给某人 */
    TASK_ASSIGNED("TASK_ASSIGNED", "任务指派"),

    /** 负责任务状态发生流转（通知任务创建人） */
    TASK_STATUS("TASK_STATUS", "任务状态变更"),

    /** 录入的反馈状态发生流转（通知录入人） */
    FEEDBACK_STATUS("FEEDBACK_STATUS", "反馈状态变更"),

    /** AI 聚类分析完成，候选卡片待确认（通知 PM） */
    AI_GENERATED("AI_GENERATED", "AI 分析完成");

    /** 存库代码 */
    private final String code;

    /** 中文名称 */
    private final String label;

    NotificationType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
