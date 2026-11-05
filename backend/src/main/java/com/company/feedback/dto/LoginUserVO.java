package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息视图对象
 *
 * <p>用于 GET /api/auth/me 接口，供前端刷新页面后恢复身份态。
 *
 * <p><b>安全边界</b>：本对象<b>不含密码字段</b>。
 * 即便密码在数据库中是 BCrypt 密文，也不应出现在任何接口的返回值里。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class LoginUserVO {

    /** 用户 ID */
    @Schema(description = "用户ID", example = "2")
    private Long userId;

    /** 登录账号 */
    @Schema(description = "登录账号", example = "pm01")
    private String username;

    /** 显示名称 */
    @Schema(description = "显示名称", example = "李产品")
    private String displayName;

    /** 角色代码 */
    @Schema(description = "角色代码", example = "PM")
    private String role;

    /** 角色中文名 */
    @Schema(description = "角色中文名", example = "产品经理")
    private String roleLabel;

    /** 所属部门 */
    @Schema(description = "所属部门", example = "产品部")
    private String dept;

    /** 可访问菜单 key 列表 */
    @Schema(description = "可访问菜单列表")
    private List<String> permissions;
}
