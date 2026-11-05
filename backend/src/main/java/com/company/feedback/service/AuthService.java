package com.company.feedback.service;

import com.company.feedback.dto.LoginRequest;
import com.company.feedback.dto.LoginResponse;
import com.company.feedback.dto.LoginUserVO;

/**
 * 认证服务接口
 *
 * @author 反馈改进助手项目组
 */
public interface AuthService {

    /**
     * 账号密码登录
     *
     * @param request 登录请求
     * @return 登录响应（含 JWT）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息视图对象
     */
    LoginUserVO getCurrentUser();
}
