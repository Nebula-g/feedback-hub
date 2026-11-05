package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 调用结果状态枚举
 *
 * <p>四种状态各自代表一种真实发生过的情形，缺一不可：
 * <ul>
 *   <li><b>SUCCESS</b>：大模型调用成功，结果已被采纳为草稿</li>
 *   <li><b>FAILED</b>：调用失败且未启用降级（或降级也失败），
 *       该次操作没有产出任何候选卡片</li>
 *   <li><b>TIMEOUT</b>：调用超时。与 FAILED 分开记录，
 *       便于事后统计"是不是超时阈值设得太紧"</li>
 *   <li><b>FALLBACK</b>：调用失败但已自动降级为规则实现，
 *       业务有产出，只是产出方不是大模型</li>
 * </ul>
 *
 * <p><b>FALLBACK 是本项目要重点展示的状态</b>：
 * 它证明"AI 挂了，但业务流程没断"。
 * 状态里 provider=RULE、error_msg=原始失败原因，
 * 一眼就能看出这次是规则兜底、以及为什么兜底。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum AiLogStatus {

    /** 调用成功 */
    SUCCESS("SUCCESS", "成功"),

    /** 调用失败（未降级） */
    FAILED("FAILED", "失败"),

    /** 调用超时 */
    TIMEOUT("TIMEOUT", "超时"),

    /** 调用失败后已降级为规则实现 */
    FALLBACK("FALLBACK", "已降级");

    /** 状态代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到返回 SUCCESS（历史数据兼容）
     *
     * @param code 状态代码
     * @return 对应的枚举
     */
    public static AiLogStatus of(String code) {
        if (code == null || code.isBlank()) {
            return SUCCESS;
        }
        for (AiLogStatus item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return SUCCESS;
    }
}
