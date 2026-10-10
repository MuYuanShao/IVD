package com.zlth.ivd.server.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 — 返回 Access Token + Refresh Token + 用户基本信息
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String role;
    private String realName;
    private Long institutionId;
    private Long departmentId;
    private java.util.Map<String, Boolean> features;
}
