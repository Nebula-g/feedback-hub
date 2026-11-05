package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 改进任务优先级枚举
 *
 * <p><b>核心约束</b>：优先级完全由产品经理手动设定，
 * AI 只能提供客观统计依据和建议分，
 * <b>绝不自动写入 priority 字段</b>。
 *
 * <p>依据选题文档约束 A4：AI 不能根据表达情绪自动决定优先级。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum TaskPriority {

    /** P0：阻塞主流程，影响多家客户 */
    P0("P0", "P0 - 阻塞主流程"),

    /** P1：影响体验但不阻塞 */
    P1("P1", "P1 - 影响体验"),

    /** P2：优化类需求 */
    P2("P2", "P2 - 体验优化");

    /** 优先级代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 P2
     *
     * @param code 优先级代码
     * @return 对应的枚举
     */
    public static TaskPriority of(String code) {
        if (code == null || code.isBlank()) {
            return P2;
        }
        for (TaskPriority item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return P2;
    }

    /**
     * 判断是否为有效优先级代码
     *
     * @param code 待校验的代码
     * @return 有效返回 true
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (TaskPriority item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
