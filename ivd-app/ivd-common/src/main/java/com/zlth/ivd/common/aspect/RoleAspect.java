package com.zlth.ivd.common.aspect;

import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色校验切面 — 拦截 @RequireRole 注解
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@Aspect
@Component
public class RoleAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(RequireRole requireRole) {
        String[] allowedRoles = requireRole.value();
        if (allowedRoles.length == 0) {
            return; // 无限制
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(40100, "未认证，请先登录");
        }

        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean hasRole = Arrays.stream(allowedRoles)
                .map(r -> "ROLE_" + r)
                .anyMatch(userRoles::contains);

        if (!hasRole) {
            log.warn("权限不足: 需要 {}, 当前 {}", Arrays.toString(allowedRoles), userRoles);
            throw new BizException(40300, "无权限访问，所需角色: " + Arrays.toString(allowedRoles));
        }
    }
}
