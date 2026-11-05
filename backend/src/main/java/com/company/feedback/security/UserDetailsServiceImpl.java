package com.company.feedback.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.feedback.entity.SysUser;
import com.company.feedback.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户详情加载服务
 *
 * <p>Spring Security 在认证时通过本类按用户名加载用户信息。
 *
 * <p><b>关于 "UserDetailsService 中查不到就抛异常" 的设计</b>：
 * 这里必须抛 UsernameNotFoundException，而不是返回 null。
 * Spring Security 的 DaoAuthenticationProvider 依赖该异常
 * 触发统一的认证失败处理流程。返回 null 会导致后续空指针。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    /**
     * 根据登录账号加载用户信息
     *
     * @param username 登录账号
     * @return 登录用户主体
     * @throws UsernameNotFoundException 账号不存在
     */
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );

        if (user == null) {
            // 日志中只记账号不记密码；即便密码错误也统一提示"账号或密码错误"，
            // 避免通过提示差异探测账号是否存在
            log.warn("登录失败：账号不存在或已禁用, username={}", username);
            throw new UsernameNotFoundException("账号或密码错误");
        }

        return new LoginUser(user);
    }
}
