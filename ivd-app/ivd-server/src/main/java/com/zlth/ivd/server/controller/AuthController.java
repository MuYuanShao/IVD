package com.zlth.ivd.server.controller;

import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import com.zlth.ivd.server.dto.LoginRequest;
import com.zlth.ivd.server.dto.LoginResponse;
import com.zlth.ivd.server.dto.RefreshRequest;
import com.zlth.ivd.server.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口 — 登录 / Token 刷新 / 用户信息
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** 用户名+密码登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("登录请求: username={}", request.getUsername());
        return Result.success(authService.login(request));
    }

    /** 刷新 Token */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }

    /** 获取当前用户信息（从 JWT 解析） */
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userinfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtTokenProvider.getUsername(token);
        String role = jwtTokenProvider.getRole(token);
        Long userId = jwtTokenProvider.getUserId(token);
        return Result.success(Map.of(
                "userId", userId,
                "username", username,
                "role", role,
                "realName", role.equals("ADMIN") ? "系统管理员" : "IVD 操作员"
        ));
    }
}
