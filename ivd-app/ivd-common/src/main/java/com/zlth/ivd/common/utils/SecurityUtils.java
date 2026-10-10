package com.zlth.ivd.common.utils;

import com.zlth.ivd.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类 — 从 JWT 认证信息中提取当前用户身份
 * <p>
 * 配合 {@code JwtAuthenticationFilter} 使用，该 Filter 已将 userId/username/role
 * 写入 {@code SecurityContextHolder}。
 *
 * @author IVD Team
 * @since 2026-07-13
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 工具类，禁止实例化
    }

    /** 获取当前登录用户 ID */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BizException(40100, "未登录或 Token 已过期");
        }
        return (Long) auth.getPrincipal();
    }

    /** 获取当前登录用户名 */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            return com.zlth.ivd.common.security.InstitutionContext.current()
                    .filter(s->"SYSTEM_JOB".equals(s.role())).map(s->"SYSTEM_JOB").orElse("anonymous");
        }
        return auth.getDetails().toString();
    }

    /** 获取当前登录用户角色（不含 ROLE_ 前缀） */
    public static String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return "USER";
        }
        String authority = auth.getAuthorities().iterator().next().getAuthority();
        return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
    }

    /** 获取当前用户 ID（可能为 null，用于审计日志等非强制场景） */
    public static Long getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** Machine-triggered business tasks record NULL, never a forged human operator. */
    public static Long getCurrentOperatorIdOrNull() {
        var scope=com.zlth.ivd.common.security.InstitutionContext.current();
        if(scope.isPresent() && "SYSTEM_JOB".equals(scope.get().role())) return null;
        return getCurrentUserId();
    }
}
