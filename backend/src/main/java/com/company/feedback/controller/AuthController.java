package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.dto.LoginRequest;
import com.company.feedback.dto.LoginResponse;
import com.company.feedback.dto.LoginUserVO;
import com.company.feedback.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 *
 * <p>本控制器下的接口全部放行（无需登录），
 * 放行规则配置在 SecurityConfig 的 /api/auth/**。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "认证管理", description = "登录与身份校验")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 账号密码登录
     *
     * @param request 登录参数
     * @return JWT 令牌与用户信息
     */
    @Operation(summary = "登录", description = "校验账号密码，返回 JWT 令牌")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 获取当前登录用户信息
     *
     * <p>用途：前端刷新页面后，用本地存储的 Token 重新拉取身份信息，
     * 避免"刷新后变成未登录"或"菜单权限丢失"。
     *
     * @return 当前登录用户信息
     */
    @Operation(summary = "获取当前登录用户", description = "根据 Token 解析当前用户身份")
    @GetMapping("/me")
    public R<LoginUserVO> me() {
        return R.ok(authService.getCurrentUser());
    }
}
