package com.zlth.ivd.server.service;

import com.zlth.ivd.server.dto.LoginRequest;
import com.zlth.ivd.server.dto.LoginResponse;
import com.zlth.ivd.server.dto.RefreshRequest;

/**
 * 认证服务接口
 *
 * @author IVD Team
 * @since 2026-07-06
 */
public interface AuthService {

    /** 用户名+密码登录，返回 Token 对 */
    LoginResponse login(LoginRequest request);

    /** 使用 Refresh Token 换取新的 Access Token */
    LoginResponse refresh(RefreshRequest request);
}
