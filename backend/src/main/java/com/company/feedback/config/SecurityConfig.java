package com.company.feedback.config;

import com.company.feedback.common.R;
import com.company.feedback.common.ResultCode;
import com.company.feedback.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置类
 *
 * <p><b>本项目的安全策略</b>：
 * <ol>
 *   <li>完全无状态：SessionCreationPolicy.STATELESS，不依赖 HttpSession</li>
 *   <li>JWT 鉴权：登录后前端每次请求携带 Token</li>
 *   <li>方法级鉴权：通过 @EnableMethodSecurity 开启 @PreAuthorize</li>
 *   <li>放行接口白名单：登录、接口文档、静态资源</li>
 * </ol>
 *
 * <p><b>为什么用方法级鉴权而不是 URL 规则</b>：
 * 本系统的权限是"角色 + 数据归属"双重判断，
 * 例如"客服只能编辑自己录入的反馈"这类规则无法用 URL 表达，
 * 必须在方法内部结合当前用户 ID 判断。
 * 因此采用 @PreAuthorize 做角色粗筛 + 方法内做数据归属细判。
 *
 * @author 反馈改进助手项目组
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /**
     * 密码加密器
     *
     * <p>使用 BCrypt：自带随机盐值，同一密码每次加密结果都不同，
     * 且密文中已包含盐与算法版本，验证时无需额外存储盐列。
     *
     * @return BCrypt 加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证提供者：把 UserDetailsService 与 PasswordEncoder 组装起来
     *
     * @return DAO 认证提供者
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * 认证管理器：登录接口调用它执行账号密码校验
     *
     * @param config Spring Security 自动配置的认证配置
     * @return 认证管理器
     * @throws Exception 配置获取失败
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤链：本系统的安全中枢
     *
     * @param http HTTP 安全配置构造器
     * @return 安全过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // 关闭 CSRF：前后端分离 + JWT 无 Cookie 认证，CSRF 攻击面不存在
                .csrf(csrf -> csrf.disable())

                // 开启跨域，配置来源见下方的 corsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 完全无状态：禁用 Session，避免服务器内存随用户数增长
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        // ---- 放行白名单 ----
                        // 登录与Token校验接口
                        .requestMatchers("/api/auth/**").permitAll()
                        // 接口文档（springdoc）
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 静态资源（本地存储的图片，生产环境由 Nginx 直接处理）
                        .requestMatchers("/uploads/**").permitAll()
                        // OPTIONS 预检请求直接放行，否则跨域会失败
                        .requestMatchers(
                                org.springframework.http.HttpMethod.OPTIONS, "/**"
                        ).permitAll()
                        // ---- 其余接口均需登录 ----
                        .anyRequest().authenticated()
                )

                // 异常处理：未登录 → 401；已登录但无权限 → 403
                // 关键点：返回 JSON 而非默认的登录页/HTML 错误页
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, ResultCode.FORBIDDEN))
                )

                // 把 JWT 过滤器插到用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域配置源
     *
     * <p><b>为什么用 allowedOriginPatterns 而不是 allowedOrigins</b>：
     * allowedOrigins("*") 配合 allowCredentials(true) 是 Spring 明确禁止的组合；
     * 而局域网联调时其他电脑的 Origin 是 http://&lt;开发机IP&gt;:5173，
     * 只列 localhost 会把这些请求全部拒成 403。
     *
     * <p><b>提示</b>：部署到 Nginx 后，前后端同源（都是 localhost:8080），
     * 实际上不再触发跨域，这份配置主要服务于开发调试阶段。
     *
     * @return 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 用 pattern 而不是 setAllowedOrigins：
        //   1. allowedOrigins("*") 与 allowCredentials(true) 是 Spring 禁止的组合；
        //   2. 局域网联调时 Origin 是 http://<开发机IP>:5173，只写 localhost 会被
        //      CorsFilter 拒成 403 "Invalid CORS request"（其他电脑无法登录）。
        // 生产环境由 Nginx 同源反代，不存在跨域，此处放宽不构成安全面。
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // 暴露给前端读取的响应头
        config.setExposedHeaders(List.of("Authorization"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 以 JSON 格式写出统一错误响应
     *
     * <p>注意：这里只声明 IOException 而非 Exception。
     * Spring Security 的 AuthenticationEntryPoint / AccessDeniedHandler
     * 接口签名只允许抛出 IOException 与 ServletException，
     * 声明更宽的 Exception 会导致 Lambda 无法编译。
     *
     * @param response   HTTP 响应
     * @param resultCode 错误码枚举
     * @throws java.io.IOException 写响应失败
     */
    private void writeJson(HttpServletResponse response, ResultCode resultCode)
            throws java.io.IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        R<Void> body = R.fail(resultCode);
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
