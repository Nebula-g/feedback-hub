package com.company.feedback.common;

import com.company.feedback.exception.BizException;
import com.company.feedback.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文工具类
 *
 * <p>作用：在任何业务代码中一行获取当前登录用户的 ID、角色、账号，
 * 无需从 Controller 层层往下传参。
 *
 * <p><b>实现原理</b>：
 * 从 Spring Security 的 SecurityContextHolder 中读取。
 * 该对象内部使用 ThreadLocal 存储，因此天然线程隔离——
 * Tomcat 的每个请求线程拿到的是各自的身份，互不干扰。
 *
 * <p><b>这个类的价值在于「让权限校验无法被遗忘」</b>：
 * 所有需要判断"这条数据是不是当前用户创建的"的地方，
 * 直接调用 {@link #getUserId()} 即可，
 * 避免了"某个接口忘记做归属校验"这类低级但致命的漏洞。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
public final class UserContext {

    /** 私有构造，防止实例化 */
    private UserContext() {
    }

    /**
     * 获取当前登录用户 ID
     *
     * @return 用户 ID
     * @throws BizException 未登录时抛出 401
     */
    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser.getUserId();
    }

    /**
     * 获取当前登录用户 ID（允许为空）
     *
     * <p>适用于"登录与否都可访问，但登录后有额外信息"的场景。
     *
     * @return 用户 ID，未登录返回 null
     */
    public static Long getUserIdOrNull() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前登录账号
     *
     * @return 登录账号，未登录返回 null
     */
    public static String getUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUsername();
    }

    /**
     * 获取当前登录用户角色
     *
     * @return 角色代码，未登录返回 null
     */
    public static String getRole() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getRole();
    }

    /**
     * 获取当前登录用户显示名称
     *
     * @return 显示名称，未登录返回 null
     */
    public static String getDisplayName() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getDisplayName();
    }

    /**
     * 判断当前用户是否拥有指定角色
     *
     * @param role 角色代码，如 "PM"
     * @return 拥有返回 true
     */
    public static boolean hasRole(String role) {
        String currentRole = getRole();
        return currentRole != null && currentRole.equalsIgnoreCase(role);
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return 是管理员返回 true
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * 获取登录用户主体对象
     *
     * @return 登录用户主体，未认证返回 null
     */
    private static LoginUser getLoginUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // 未登录时 Spring Security 会把 principal 设成字符串 "anonymousUser"
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }

        return null;
    }
}
