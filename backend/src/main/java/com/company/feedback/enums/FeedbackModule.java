package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 功能模块枚举
 *
 * <p>这是本项目的核心分类维度，也是 AI 聚类的<b>硬分组依据</b>。
 *
 * <p>为什么必须硬分组：
 * 跨模块的反馈即使关键词相似也绝不能合并。例如
 * "报销单填写时的提示看不懂" 和 "发票上传失败提示看不懂"
 * 属于不同模块，是两个独立问题。
 * 这一层用规则兜底，保证 AI 再怎么出错也不会犯跨模块的错。
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum FeedbackModule {

    /** 报销单填写 */
    EXPENSE_FORM("EXPENSE_FORM", "报销单填写"),

    /** 发票上传 */
    INVOICE_UPLOAD("INVOICE_UPLOAD", "发票上传"),

    /** 审批进度 */
    APPROVAL_PROGRESS("APPROVAL_PROGRESS", "审批进度"),

    /** 退回修改 */
    RETURN_MODIFY("RETURN_MODIFY", "退回修改"),

    /** 其他 */
    OTHER("OTHER", "其他");

    /** 枚举代码（入库值） */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举
     *
     * @param code 枚举代码
     * @return 对应的枚举，未找到时返回 OTHER
     */
    public static FeedbackModule of(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        for (FeedbackModule item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return OTHER;
    }

    /**
     * 判断是否为有效模块代码
     *
     * @param code 待校验的代码
     * @return 有效返回 true
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (FeedbackModule item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
