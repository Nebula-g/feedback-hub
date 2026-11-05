package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 实现类型枚举
 *
 * <p>本项目采用<b>可切换双实现</b>策略：
 * <ul>
 *   <li>LLM —— 真实大模型调用（HTTP）</li>
 *   <li>RULE —— 本地规则兜底（关键词 + 模板）</li>
 * </ul>
 *
 * <p>设计意义：
 * 当 LLM 调用失败、超时或未配置 Key 时，系统自动降级为 RULE，
 * 保证核心业务流程<b>永不因 AI 不可用而中断</b>。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum AiProvider {

    /** 真实大模型 */
    LLM("LLM", "大模型"),

    /** 本地规则兜底 */
    RULE("RULE", "规则兜底");

    /** 类型代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 RULE（安全第一）
     *
     * @param code 类型代码
     * @return 对应的枚举
     */
    public static AiProvider of(String code) {
        if (code == null || code.isBlank()) {
            return RULE;
        }
        for (AiProvider item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return RULE;
    }
}
