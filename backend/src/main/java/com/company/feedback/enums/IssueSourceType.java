package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问题来源类型枚举
 *
 * <p>用于区分一条已确认的 issue 是怎么产生的，这是本项目
 * 「AI 产出与人工决策物理分离」原则在数据层面的落点之一：
 * <ul>
 *   <li><b>AI</b> —— 由 AI 候选卡片确认生成。
 *       此时 {@code ai_draft_content} 有值（AI 原话），
 *       可用于事后比对「PM 是否改动了 AI 的产出」。</li>
 *   <li><b>MANUAL</b> —— PM 手工创建，全程无 AI 参与。
 *       此时 {@code ai_draft_content} 为空，
 *       表示这条结论<b>完全由人负责</b>，不属于 AI 产出。</li>
 * </ul>
 *
 * <p><b>为什么要单独抽成枚举而不是散落的 String 常量？</b>
 * 阶段 5 契约核对时发现：{@code "MANUAL"} / {@code "AI"} 这两个值
 * 原本只是写在 IssueServiceImpl 里的 private static final String，
 * 前端则在页面里硬编码同样的字符串。这带来两个问题：
 * <ol>
 *   <li>没有单一事实来源，任一侧拼错都不会报错，只会静默产生脏数据；</li>
 *   <li>前端下拉选项的值无法与后端字典自动核对（本项目的
 *       contract_check.py 就是靠枚举类做差集比对的）。</li>
 * </ol>
 * 抽成枚举后，后端写入有类型约束，前端选项值也能被自动核对。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum IssueSourceType {

    /** AI 候选卡片确认生成 */
    AI("AI", "AI 生成"),

    /** 产品经理手工创建 */
    MANUAL("MANUAL", "人工创建");

    /** 类型代码（落库值） */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 MANUAL（保守默认）
     *
     * <p>默认取 MANUAL 而不是 AI：当来源无法判定时，
     * 宁可把归属记成"人工负责"，也不要把人的决策错误地记成 AI 的产出。
     *
     * @param code 类型代码
     * @return 对应的枚举
     */
    public static IssueSourceType of(String code) {
        if (code == null || code.isBlank()) {
            return MANUAL;
        }
        for (IssueSourceType item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return MANUAL;
    }
}
