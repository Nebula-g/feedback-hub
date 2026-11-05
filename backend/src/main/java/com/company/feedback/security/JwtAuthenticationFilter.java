package com.company.feedback.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * <p>执行时机：在 UsernamePasswordAuthenticationFilter 之前，
 * 每个请求进入业务 Controller 之前。
 *
 * <p><b>本过滤器做了三件事</b>：
 * <ol>
 *   <li>从 Authorization 请求头取出 "Bearer xxx" 格式的 Token</li>
 *   <li>校验签名与有效期，解析出用户 ID 与角色</li>
 *   <li>构造已认证的身份对象，写入 SecurityContext</li>
 * </ol>
 *
 * <p><b>为什么继承 OncePerRequestFilter</b>：
 * 普通 Filter 在一次请求中可能被多个过滤链重复执行（如转发、包含），
 * OncePerRequestFilter 保证逻辑只执行一次，避免重复解析 Token。
 *
 * <p><b>关键设计：解析失败时不抛异常，而是清空上下文继续往下走</b>。
 * 原因：Token 无效这件事应该由后方的 AuthenticationEntryPoint 统一处理，
 * 返回标准 401 JSON。如果在此处直接抛异常，
 * 异常会被 Servlet 容器捕获，返回的是 HTML 错误页，前后端分离场景下前端无法解析。
 *
 * @author 反馈改进助手项目组
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * 过滤逻辑主入口
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 提取 Token
        String token = resolveToken(request);

        // 2. 有 Token 且有效 → 写入安全上下文
        if (StringUtils.hasText(token) && !jwtTokenProvider.isTokenExpired(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                String username = jwtTokenProvider.getUsername(token);

                if (userId != null && StringUtils.hasText(username)) {
                    // 重新从数据库加载，保证拿到的是最新角色与状态
                    // （角色可能在 Token 签发后被管理员调整）
                    var userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT 认证成功, userId={}, role={}",
                            userId, jwtTokenProvider.getRole(token));
                }
            } catch (Exception e) {
                // 认证失败必须清空上下文，否则会残留上一次的身份
                log.warn("JWT 认证失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        // 3. 无论如何都放行——是否拦截由 SecurityConfig 的授权规则决定
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中解析 Token
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>Authorization: Bearer eyJhbGci...</li>
     *   <li>Authorization: eyJhbGci...（兼容部分老客户端）</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return Token 字符串，不存在返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }

        // 去掉 "Bearer " 前缀（注意末尾空格）
        if (bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return bearerToken;
    }
}
