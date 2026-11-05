package com.company.feedback.security;

import com.company.feedback.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（Spring Security 的 UserDetails 实现）
 *
 * <p>作用：把数据库中的 SysUser 包装成 Spring Security 认识的身份对象，
 * 供认证过滤器存入 SecurityContext，业务代码随时可取。
 *
 * <p><b>角色前缀约定</b>：
 * Spring Security 的默认习惯是角色带 "ROLE_" 前缀，
 * 因此这里统一加上前缀。这样在 @PreAuthorize 中
 * 既可以用 hasRole('PM')（自动补前缀），
 * 也可以用 hasAuthority('ROLE_PM')。
 *
 * <p><b>为什么不直接让 SysUser 实现 UserDetails</b>：
 * 实体类应当保持纯粹——它是数据库表的映射，不该耦合安全框架的契约。
 * 一旦实体实现 UserDetails，序列化、JSON 输出、DTO 转换都会带上
 * 密码、权限这些不该外泄的东西。用包装类隔离开更安全。
 *
 * @author 反馈改进助手项目组
 */
@Getter
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private final Long userId;

    /** 登录账号 */
    private final String username;

    /** 加密后的密码 */
    private final String password;

    /** 显示名称 */
    private final String displayName;

    /** 角色代码：CS / PM / DEV / ADMIN */
    private final String role;

    /** 权限集合 */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造登录用户主体
     *
     * @param user 数据库用户实体
     */
    public LoginUser(SysUser user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        // 角色统一加 ROLE_ 前缀，符合 Spring Security 的 hasRole 约定
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    /**
     * 账号是否未过期
     *
     * <p>本项目不做账号有效期管理，恒返回 true。
     * 账号启停由 sys_user.status 字段控制。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账号是否未锁定
     *
     * <p>本项目不做登录失败锁定，恒返回 true。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账号是否启用
     *
     * <p>对应 sys_user.status 字段：1-启用 0-禁用
     * 这里返回 true 是因为禁用判断已在 UserDetailsService 中提前拦截，
     * 走到这里的用户必然是启用状态。
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
