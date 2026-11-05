package com.company.feedback.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类
 *
 * <p>职责：生成 Token、校验 Token、从 Token 中解析用户信息。
 *
 * <p><b>技术选型说明</b>：
 * 使用 JJWT 0.12.x，因为它是目前唯一完整支持 Jakarta EE 命名空间
 * （Spring Boot 3 的强制要求）的 JWT 库。
 * 0.11.x 及更早版本依赖 javax.*，在 Boot 3 下会报 ClassNotFoundException。
 *
 * <p><b>Token 中存放了什么</b>：
 * <ul>
 *   <li>subject：用户 ID（字符串形式）</li>
 *   <li>username：登录账号</li>
 *   <li>role：角色代码（CS/PM/DEV/ADMIN）</li>
 *   <li>displayName：显示名称（前端直接展示，免去额外查询）</li>
 * </ul>
 *
 * <p><b>安全边界</b>：Token 中<b>绝不放密码</b>，
 * 也不放任何敏感业务数据——Token 只是 Base64 编码，可被解码查看。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 密钥 */
    @Value("${jwt.secret}")
    private String secret;

    /** 过期时间（毫秒） */
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 生成 JWT Token
     *
     * @param userId      用户 ID
     * @param username    登录账号
     * @param role        角色代码
     * @param displayName 显示名称
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username,
                                String role, String displayName) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                // 主体：存放用户 ID，解析时作为唯一标识
                .subject(String.valueOf(userId))
                // 自定义声明：前端可直接读取用于菜单渲染
                .claim("username", username)
                .claim("role", role)
                .claim("displayName", displayName)
                // 签发时间
                .issuedAt(now)
                // 过期时间
                .expiration(expireDate)
                // 使用 HS256 算法签名
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 从 Token 中解析 Claims（载荷）
     *
     * @param token JWT 字符串
     * @return 载荷对象
     * @throws ExpiredJwtException Token 已过期
     * @throws JwtException        Token 格式错误或签名校验失败
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断 Token 是否过期
     *
     * @param token JWT 字符串
     * @return 已过期返回 true；格式错误也返回 true（视为不可用）
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            // JJWT 遇到过期 Token 会直接抛异常，而非返回 Claims
            return true;
        } catch (JwtException e) {
            log.warn("Token 解析失败，视为过期: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT 字符串
     * @return 用户 ID，解析失败返回 null
     */
    public Long getUserId(String token) {
        try {
            return Long.parseLong(parseToken(token).getSubject());
        } catch (Exception e) {
            log.warn("从 Token 提取用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取角色代码
     *
     * @param token JWT 字符串
     * @return 角色代码，解析失败返回 null
     */
    public String getRole(String token) {
        try {
            return parseToken(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取登录账号
     *
     * @param token JWT 字符串
     * @return 登录账号，解析失败返回 null
     */
    public String getUsername(String token) {
        try {
            return parseToken(token).get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取 Token 有效期（毫秒）
     *
     * <p>供登录接口返回给前端，前端据此设置定时刷新或过期提示。
     *
     * @return 有效期（毫秒）
     */
    public Long getExpiration() {
        return expiration;
    }

    /**
     * 获取签名密钥
     *
     * <p>注意：HS256 算法要求密钥长度不少于 256 bit（32 字节）。
     * 如果 application.yml 中的 jwt.secret 配得太短，
     * 这里会抛出 WeakKeyException，启动时直接失败——
     * 这是好事，能尽早暴露配置问题。
     *
     * @return HMAC 签名密钥
     */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
