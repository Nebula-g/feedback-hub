package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈来源渠道枚举
 *
 * <p>依据：选题文档中提到反馈可能来自
 * "客服工单、使用调查和客户沟通记录"。
 *
 * <p>注意：本字段属于<b>推测设计</b>，
 * 真实企业反馈工单的渠道划分需后续查证。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum FeedbackSource {

    /** 客服工单 */
    TICKET("TICKET", "客服工单"),

    /** 使用调查 */
    SURVEY("SURVEY", "使用调查"),

    /** 客户沟通记录 */
    CALL("CALL", "客户沟通");

    /** 枚举代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举，未找到时返回 TICKET
     *
     * @param code 枚举代码
     * @return 对应的枚举
     */
    public static FeedbackSource of(String code) {
        if (code == null || code.isBlank()) {
            return TICKET;
        }
        for (FeedbackSource item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return TICKET;
    }

    /**
     * 判断是否为有效来源代码
     *
     * @param code 待校验的代码
     * @return 有效返回 true
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (FeedbackSource item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
