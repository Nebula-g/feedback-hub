package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈与问题关联类型枚举
 *
 * <p>用于区分关联的来源，便于事后审计：
 * <ul>
 *   <li>AI —— 由 AI 聚类建议的关联</li>
 *   <li>PM —— 由产品经理手工建立的关联</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum LinkType {

    /** AI 建议的关联 */
    AI("AI", "AI 建议"),

    /** 产品经理手工关联 */
    PM("PM", "人工关联");

    /** 类型代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 PM（保守默认）
     *
     * @param code 类型代码
     * @return 对应的枚举
     */
    public static LinkType of(String code) {
        if (code == null || code.isBlank()) {
            return PM;
        }
        for (LinkType item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return PM;
    }
}
