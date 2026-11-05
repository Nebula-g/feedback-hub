package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 反馈状态枚举（含状态机流转规则）
 *
 * <p>状态流转图：
 * <pre>
 *   NEW（未归类）
 *     ├─→ LINKED（已关联问题） ─→ CLOSED（随任务完成）
 *     └─→ IGNORED（PM 判为无效）
 * </pre>
 *
 * <p>禁止的跳转：
 * <ul>
 *   <li>LINKED → NEW：需由 PM 解除关联并留下记录，不能直接回退</li>
 *   <li>CLOSED → 任何状态：终态不可逆</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum FeedbackStatus {

    /** 未归类：刚录入，尚未关联到任何问题 */
    NEW("NEW", "未归类", Set.of("LINKED", "IGNORED")),

    /** 已关联：已归入某个候选卡片或正式问题 */
    LINKED("LINKED", "已关联", Set.of("CLOSED", "NEW")),

    /** 已关闭：关联的任务已完成 */
    CLOSED("CLOSED", "已关闭", Set.of()),

    /** 无效：被 PM 判定为无法归类或无意义 */
    IGNORED("IGNORED", "无效", Set.of());

    /** 状态代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 允许跳转到的目标状态集合
     *
     * <p>说明：LINKED 允许跳转到 NEW，是为了支持 PM 驳回候选卡片后
     * 让反馈重新回到待归类池；但该操作必须由 PM 发起并记录操作说明，
     * 不能由系统静默回退。
     */
    private final Set<String> nextStatuses;

    /**
     * 判断是否允许从当前状态跳转到目标状态
     *
     * @param targetCode 目标状态代码
     * @return 允许返回 true，否则 false
     */
    public boolean canTransitTo(String targetCode) {
        return nextStatuses.contains(targetCode);
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 状态代码
     * @return 对应的枚举，未找到返回 null
     */
    public static FeedbackStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (FeedbackStatus item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }
}
