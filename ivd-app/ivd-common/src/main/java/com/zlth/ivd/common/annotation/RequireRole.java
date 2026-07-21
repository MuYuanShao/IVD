package com.zlth.ivd.common.annotation;

import java.lang.annotation.*;

/**
 * RBAC 角色校验注解 — 标注在 Controller 方法上
 * <p>
 * 用法: @RequireRole("SYSTEM_ADMIN")  // 仅管理员可访问
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许访问的角色列表 */
    String[] value() default {};
}
