package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 改进任务状态枚举（含状态机流转规则）
 *
 * <p>状态流转图：
 * <pre>
 *   TODO（待开发）
 *     ├─→ IN_PROGRESS（开发中）→ PENDING_VERIFY（待验证）→ DONE（已完成）
 *     │                              │
 *     │                              └──→ IN_PROGRESS（验证不通过打回）
 *     └─→ WONT_DO（PM 决定不做）
 * </pre>
 *
 * <p>禁止的跳转（本项目的核心业务规则）：
 * <ul>
 *   <li>TODO → DONE：必须走完全流程，不能一步到位</li>
 *   <li>TODO → PENDING_VERIFY：不得跳过"开发中"</li>
 *   <li>DONE → 任何业务状态：终态不可逆，如需重开应由 PM 新建任务</li>
 * </ul>
 *
 * <p>需要强制填写说明的跳转：
 * <ul>
 *   <li>PENDING_VERIFY → IN_PROGRESS（验证不通过，必须写明打回原因）</li>
 *   <li>任意状态 → WONT_DO（必须写明关闭理由）</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {

    /** 待开发：任务已建立，等待开发接手 */
    TODO("TODO", "待开发", Set.of("IN_PROGRESS", "WONT_DO")),

    /** 开发中：开发已接手 */
    IN_PROGRESS("IN_PROGRESS", "开发中", Set.of("PENDING_VERIFY", "WONT_DO")),

    /** 待验证：开发完成，等待测试验证 */
    PENDING_VERIFY("PENDING_VERIFY", "待验证", Set.of("DONE", "IN_PROGRESS")),

    /** 已完成：验证通过 */
    DONE("DONE", "已完成", Set.of()),

    /** 已关闭：PM 决定不做 */
    WONT_DO("WONT_DO", "已关闭", Set.of());

    /** 状态代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /** 允许跳转到的目标状态集合 */
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
    public static TaskStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (TaskStatus item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断该状态跳转是否必须填写说明
     *
     * <p>规则：
     * <ul>
     *   <li>待验证 → 开发中：验证不通过，必须写明原因</li>
     *   <li>任意 → 已关闭：必须写明关闭理由</li>
     * </ul>
     *
     * @param from 原状态
     * @param to   目标状态
     * @return 必须填写返回 true
     */
    public static boolean requireRemark(String from, String to) {
        if (to == null) {
            return false;
        }
        // 关闭任务必须说明理由
        if (WONT_DO.code.equals(to)) {
            return true;
        }
        // 验证不通过打回必须说明原因
        return PENDING_VERIFY.code.equals(from) && IN_PROGRESS.code.equals(to);
    }
}
