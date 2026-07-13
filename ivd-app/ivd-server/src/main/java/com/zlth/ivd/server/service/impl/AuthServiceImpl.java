package com.zlth.ivd.server.service.impl;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import com.zlth.ivd.server.dto.LoginRequest;
import com.zlth.ivd.server.dto.LoginResponse;
import com.zlth.ivd.server.dto.RefreshRequest;
import com.zlth.ivd.server.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 认证服务实现 — 一期使用内存用户（后续接入 DB）
 * <p>
 * ⚠️ 安全警告：当前为 MVP 开发阶段，使用硬编码内存用户。
 * 上线前必须迁移到 sys_user 表 + 强制修改默认密码。
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    /** 启动时动态构建（不同用户不同密码），避免共享 BCrypt 哈希 */
    private Map<String, InternalUser> users;

    public AuthServiceImpl(JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    void initUsers() {
        log.warn("============================================================");
        log.warn("⚠️  使用硬编码内存用户 —— 仅用于 MVP 开发/测试！");
        log.warn("⚠️  上线前务必迁移至 sys_user 表并强制修改默认密码。");
        log.warn("    admin    / admin123");
        log.warn("    ivduser  / ivduser123");
        log.warn("============================================================");

        this.users = Map.of(
                "admin", new InternalUser(1L, "admin",
                        passwordEncoder.encode("admin123"),
                        "ADMIN", "系统管理员"),
                "ivduser", new InternalUser(2L, "ivduser",
                        passwordEncoder.encode("ivduser123"),
                        "USER", "IVD 操作员")
        );
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        InternalUser user = users.get(request.getUsername());
        if (user == null) {
            throw new BizException(40100, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.passwordHash)) {
            throw new BizException(40100, "用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateToken(user.userId, user.username, user.role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.userId);

        log.info("用户登录成功: username={}, role={}", user.username, user.role);
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.userId)
                .username(user.username)
                .role(user.role)
                .realName(user.realName)
                .build();
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BizException(40100, "Refresh Token 无效或已过期，请重新登录");
        }

        Long userId = jwtTokenProvider.getUserId(token);
        InternalUser user = users.values().stream()
                .filter(u -> u.userId.equals(userId))
                .findFirst()
                .orElseThrow(() -> new BizException(40100, "用户不存在"));

        String newAccessToken = jwtTokenProvider.generateToken(user.userId, user.username, user.role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.userId);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.userId)
                .username(user.username)
                .role(user.role)
                .realName(user.realName)
                .build();
    }

    /** 内部用户模型（后续迁移到 sys_user 表） */
    private record InternalUser(Long userId, String username, String passwordHash, String role, String realName) {}
}
