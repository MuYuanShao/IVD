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
    private final com.zlth.ivd.system.service.AuthenticationDirectoryService directory;
    @org.springframework.beans.factory.annotation.Value("${ivd.isolation.enabled:false}")
    private boolean isolationEnabled;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = directory.findForLogin(request.getUsername());

        if (user == null) {
            throw new BizException(40100, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(40100, "用户名或密码错误");
        }

        requireInstitution(user);
        String accessToken = accessToken(user);
        String refreshToken = refreshToken(user);

        log.info("用户登录成功: username={}, role={}", user.getUsername(), user.getRole());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .realName(user.getRealName())
                .institutionId(user.getInstitutionId()).departmentId(user.getDeptId())
                .features(java.util.Map.of("labCatalog", isolationEnabled))
                .build();
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();
        if (!jwtTokenProvider.validateRefreshToken(token)) {
            throw new BizException(40100, "Refresh Token 无效或已过期，请重新登录");
        }

        Long userId = jwtTokenProvider.getUserId(token);
        SysUser user = directory.findActiveUser(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BizException(40100, "用户不存在或已停用");
        }

        requireInstitution(user);
        if (isolationEnabled && !jwtTokenProvider.validateInstitutionRefreshToken(token, user.getInstitutionId())) {
            throw new BizException(40100, "机构上下文已变更，请重新登录");
        }
        String newAccessToken = accessToken(user);
        String newRefreshToken = refreshToken(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .realName(user.getRealName())
                .institutionId(user.getInstitutionId()).departmentId(user.getDeptId())
                .features(java.util.Map.of("labCatalog", isolationEnabled))
                .build();
    }

    private String refreshToken(SysUser user) {
        return isolationEnabled ? jwtTokenProvider.generateRefreshToken(user.getId(), user.getInstitutionId())
                : jwtTokenProvider.generateRefreshToken(user.getId());
    }

    private String accessToken(SysUser user) {
        return isolationEnabled ? jwtTokenProvider.generateToken(user.getId(),user.getUsername(),user.getRole(),user.getInstitutionId(),user.getDeptId())
                : jwtTokenProvider.generateToken(user.getId(),user.getUsername(),user.getRole());
    }
    private void requireInstitution(SysUser user) {
        if (isolationEnabled && (user.getInstitutionId() == null || directory.activeInstitution(user.getInstitutionId()) == null))
            throw new BizException(40300,"用户所属机构未配置或已停用");
    }

    @Override public java.util.Map<String,Object> currentUser() {
        var user=directory.findActiveUser(com.zlth.ivd.common.utils.SecurityUtils.getCurrentUserId());
        if(user==null) throw new BizException(40100,"账号已停用");
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("userId",user.getId()); result.put("username",user.getUsername()); result.put("role",user.getRole()); result.put("realName",user.getRealName());
        var scope=com.zlth.ivd.common.security.InstitutionContext.current();
        result.put("institutionId",scope.map(s->s.institutionId()).orElse(user.getInstitutionId()));
        result.put("homeInstitutionId",user.getInstitutionId());
        result.put("departmentId",scope.isPresent()?scope.get().departmentId():user.getDeptId());
        result.put("features",java.util.Map.of("labCatalog", isolationEnabled));
        return result;
    }
}
