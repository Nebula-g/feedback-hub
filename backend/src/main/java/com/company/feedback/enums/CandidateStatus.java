package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * AI 候选问题卡片状态枚举（含状态机流转规则）
 *
 * <p><b>重要定位</b>：候选卡片是「草稿」，不是业务事实。
 * 只有 PM 确认后才生成正式的 issue 记录。
 *
 * <p>状态流转图：
 * <pre>
 *   DRAFT（AI 生成，待 PM 处理）
 *     ├─→ CONFIRMED（直接确认）  ─┐
 *     ├─→ MODIFIED（修改后确认） ─┼─→ 生成正式 issue
 *     ├─→ MERGED（并入已有问题）─┘
 *     ├─→ SPLITTED（手动拆分）──→ 生成 N 张新 DRAFT 卡片
 *     └─→ REJECTED（驳回）
 * </pre>
 *
 * <p>禁止的跳转：
 * <ul>
 *   <li>REJECTED → CONFIRMED：驳回后必须重新生成候选，不能直接确认</li>
 *   <li>CONFIRMED / MERGED → DRAFT：已确认结果不可逆</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum CandidateStatus {

    /**
     * 草稿：AI 刚生成，等待 PM 处理
     *
     * <p>SPLITTED 也是 DRAFT 的合法去向：当 PM 判定一张卡片把
     * 多个不同问题混在了一起（典型如一条反馈里同时写了
     * "上传失败"和"看不到退回原因"），可以把它拆成多张新卡片分别处理。
     */
    DRAFT("DRAFT", "待处理",
            Set.of("CONFIRMED", "MODIFIED", "MERGED", "SPLITTED", "REJECTED")),

    /** 已确认：PM 直接确认，生成正式问题 */
    CONFIRMED("CONFIRMED", "已确认", Set.of()),

    /** 修改后确认：PM 修改了标题/描述后确认，生成正式问题 */
    MODIFIED("MODIFIED", "修改后确认", Set.of()),

    /** 已合并：PM 将其并入已有问题，不生成新问题 */
    MERGED("MERGED", "已合并", Set.of()),

    /** 已驳回：PM 判定不成立，关联反馈回到待归类 */
    REJECTED("REJECTED", "已驳回", Set.of()),

    /**
     * 已拆分：PM 判定该卡片混合了多个独立问题，已手动拆分为多张新候选卡片
     *
     * <p>原卡片保留（AI 原稿可追溯），不再参与占用统计——
     * 它的关联反馈已由拆分出的新卡片接管，不会掉出归类流程。
     */
    SPLITTED("SPLITTED", "已拆分", Set.of());

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
    public static CandidateStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (CandidateStatus item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断该状态是否表示"已处理完毕"
     *
     * <p>用于工作台筛选：待处理 vs 已处理
     *
     * @param code 状态代码
     * @return 已处理返回 true
     */
    public static boolean isHandled(String code) {
        return CONFIRMED.code.equals(code)
                || MODIFIED.code.equals(code)
                || MERGED.code.equals(code)
                || REJECTED.code.equals(code)
                || SPLITTED.code.equals(code);
    }
}
