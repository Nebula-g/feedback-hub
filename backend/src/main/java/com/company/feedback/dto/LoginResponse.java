package com.company.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应结果
 *
 * <p><b>为什么返回这么多字段</b>：
 * 前端登录后需要立刻知道"我是谁、我是什么角色、我能看哪些菜单"，
 * 如果只返回 token，前端还得再发一次请求查用户信息，
 * 多一次往返就多一次失败的可能。
 *
 * @author 反馈改进助手项目组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    /** JWT 令牌 */
    @Schema(description = "JWT令牌，后续请求需在 Authorization 头中携带")
    private String token;

    /** 令牌类型，固定为 Bearer */
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    /** 有效期（毫秒） */
    @Schema(description = "有效期（毫秒）", example = "28800000")
    private Long expiresIn;

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

    /** 该角色可访问的菜单 key 列表 */
    @Schema(description = "可访问菜单列表")
    private List<String> permissions;
}
