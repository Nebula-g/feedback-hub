package com.company.feedback.service.impl;

import com.company.feedback.common.ResultCode;
import com.company.feedback.common.UserContext;
import com.company.feedback.dto.LoginRequest;
import com.company.feedback.dto.LoginResponse;
import com.company.feedback.dto.LoginUserVO;
import com.company.feedback.entity.SysUser;
import com.company.feedback.enums.RoleEnum;
import com.company.feedback.exception.BizException;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.security.JwtTokenProvider;
import com.company.feedback.security.LoginUser;
import com.company.feedback.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现
 *
 * <p><b>登录流程</b>：
 * <ol>
 *   <li>AuthenticationManager 调用 UserDetailsService 加载用户</li>
 *   <li>DaoAuthenticationProvider 用 BCrypt 比对密码</li>
 *   <li>校验通过后生成 JWT 返回</li>
 * </ol>
 *
 * <p><b>为什么把密码校验交给 Spring Security 而不是自己比对</b>：
 * 自己写 passwordEncoder.matches(input, hash) 也能工作，
 * 但会绕开 Spring Security 的账号状态检查（锁定、禁用、过期）
 * 和统一的失败事件发布机制。交给框架能保证行为一致、可审计。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;

    /**
     * 账号密码登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @Override
    public LoginResponse login(LoginRequest request) {

        // 1. 交给 Spring Security 完成账号存在性 + 密码 + 账号状态校验
        //    失败时抛出 BadCredentialsException
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            // 统一提示，不区分"账号不存在"与"密码错误"，
            // 防止攻击者通过提示差异枚举有效账号
            log.warn("登录失败，账号或密码错误, username={}", request.getUsername());
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 2. 取出认证主体，生成 Token
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        String token = jwtTokenProvider.generateToken(
                loginUser.getUserId(),
                loginUser.getUsername(),
                loginUser.getRole(),
                loginUser.getDisplayName()
        );

        log.info("用户登录成功, userId={}, role={}",
                loginUser.getUserId(), loginUser.getRole());

        // 3. 组装响应
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpiration())
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .displayName(loginUser.getDisplayName())
                .role(loginUser.getRole())
                .roleLabel(resolveRoleLabel(loginUser.getRole()))
                .permissions(resolvePermissions(loginUser.getRole()))
                .build();
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息视图对象
     */
    @Override
    public LoginUserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);

        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "登录用户不存在");
        }

        return LoginUserVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .roleLabel(resolveRoleLabel(user.getRole()))
                .dept(user.getDept())
                .permissions(resolvePermissions(user.getRole()))
                .build();
    }

    /**
     * 角色代码转中文名
     *
     * @param roleCode 角色代码
     * @return 中文名，未识别时返回原代码
     */
    private String resolveRoleLabel(String roleCode) {
        RoleEnum role = RoleEnum.of(roleCode);
        return role == null ? roleCode : role.getLabel();
    }

    /**
     * 根据角色返回可访问菜单
     *
     * <p>菜单权限放在服务端返回而不是前端硬编码，
     * 是为了让"权限"这一件事只有一个真相来源。
     * 注意：这里只是前端展示层面的控制，
     * 后端每个接口仍有独立的 @PreAuthorize 校验，
     * 不会因为前端隐藏了按钮就放松后端鉴权。
     *
     * @param roleCode 角色代码
     * @return 菜单 key 列表
     */
    private List<String> resolvePermissions(String roleCode) {
        return switch (roleCode == null ? "" : roleCode.toUpperCase()) {
            case "CS" -> List.of("feedback:list", "feedback:create", "feedback:import");
            case "PM" -> List.of("feedback:list", "candidate:list", "candidate:handle",
                    "issue:list", "issue:create", "task:list", "task:create",
                    "task:priority", "ai:log");
            case "DEV" -> List.of("feedback:list", "issue:list",
                    "task:list", "task:transit");
            case "ADMIN" -> List.of("user:list", "user:create",
                    "user:update", "user:delete");
            default -> List.of();
        };
    }
}
