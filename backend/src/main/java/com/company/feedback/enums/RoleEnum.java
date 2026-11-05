package com.company.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统角色枚举
 *
 * <p>共 4 类角色，其中前 3 类为业务角色，
 * ADMIN 为基础设施角色，不参与任何业务决策。
 *
 * <p>权限边界（对应需求文档第 4.2 节）：
 * <ul>
 *   <li>CS —— 录入反馈；不能定优先级、不能删改原文</li>
 *   <li>PM —— 唯一有权设定优先级；不能越过 DEV 标记完成</li>
 *   <li>DEV —— 推进任务状态；不能改优先级、不能跳状态</li>
 *   <li>ADMIN —— 仅维护账号与角色</li>
 * </ul>
 *
 * @author 反馈改进助手项目组
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    /** 客服人员 */
    CS("CS", "客服人员"),

    /** 产品经理 */
    PM("PM", "产品经理"),

    /** 开发/测试人员 */
    DEV("DEV", "开发测试"),

    /** 系统管理员（基础设施角色） */
    ADMIN("ADMIN", "系统管理员");

    /** 角色代码 */
    private final String code;

    /** 中文显示名 */
    private final String label;

    /**
     * 根据代码获取枚举
     *
     * @param code 角色代码
     * @return 对应的枚举，未找到返回 null
     */
    public static RoleEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (RoleEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否为业务角色（非基础设施角色）
     *
     * @param code 角色代码
     * @return 是业务角色返回 true
     */
    public static boolean isBusinessRole(String code) {
        return CS.code.equals(code) || PM.code.equals(code) || DEV.code.equals(code);
    }
}
