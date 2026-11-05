package com.company.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统账号实体
 *
 * <p>对应表：sys_user
 *
 * <p>职责：登录认证与角色权限控制，属基础设施，非业务对象。
 *
 * @author 反馈改进助手项目组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 登录账号（唯一）
     */
    @TableField("username")
    private String username;

    /**
     * BCrypt 加密后的密码
     *
     * <p>安全约定：任何情况下都不存储明文密码，
     * 也不在任何日志、接口返回值中出现该字段。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 角色：CS / PM / DEV / ADMIN
     */
    @TableField("role")
    private String role;

    /**
     * 所属部门
     */
    @TableField("dept")
    private String dept;

    /**
     * 状态：1-启用 0-禁用
     */
    @TableField("status")
    private Integer status;
}
