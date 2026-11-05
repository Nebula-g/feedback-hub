package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 业务类型枚举
 *
 * <p>对应 AI 的三个调用点：
 * <ol>
 *   <li>EXTRACT —— 单条反馈的结构化提取（涉及模块/问题现象/用户诉求）</li>
 *   <li>CLUSTER —— 同模块内多条反馈的语义聚类，生成候选卡片</li>
 *   <li>DRAFT —— 为已确认问题生成需求说明与验收条件</li>
 * </ol>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum AiBizType {

    /** 单条反馈提取 */
    EXTRACT("EXTRACT", "单条提取"),

    /** 相似反馈聚类 */
    CLUSTER("CLUSTER", "聚类成卡片"),

    /** 需求与验收条件起草 */
    DRAFT("DRAFT", "起草需求");

    /** 类型代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 null
     *
     * @param code 类型代码
     * @return 对应的枚举
     */
    public static AiBizType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (AiBizType item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }
}
