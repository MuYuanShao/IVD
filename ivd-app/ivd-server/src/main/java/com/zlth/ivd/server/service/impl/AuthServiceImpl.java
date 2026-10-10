package com.zlth.ivd.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import com.zlth.ivd.server.dto.LoginRequest;
import com.zlth.ivd.server.dto.LoginResponse;
import com.zlth.ivd.server.dto.RefreshRequest;
import com.zlth.ivd.server.service.AuthService;
import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现 — 基于 sys_user 数据库表
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper sysUserMapper;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getStatus, 1));

        if (user == null) {
            throw new BizException(40100, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(40100, "用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("用户登录成功: username={}, role={}", user.getUsername(), user.getRole());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .realName(user.getRealName())
                .build();
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();
        if (!jwtTokenProvider.validateRefreshToken(token)) {
            throw new BizException(40100, "Refresh Token 无效或已过期，请重新登录");
        }

        Long userId = jwtTokenProvider.getUserId(token);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BizException(40100, "用户不存在或已停用");
        }

        String newAccessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .realName(user.getRealName())
                .build();
    }
}
