package com.zlth.ivd.common.security;

import com.zlth.ivd.common.exception.BizException;
import java.util.Objects;
import java.util.function.Supplier;

/** Explicit request/job scope. Never inherited by worker threads or inferred from a default tenant. */
public final class InstitutionContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private InstitutionContext() { }

    public record Scope(long institutionId, Long departmentId, Long actorId, String role) {
        public Scope {
            if (institutionId <= 0 || role == null || role.isBlank()
                    || (actorId == null ? !"SYSTEM_JOB".equals(role) : actorId <= 0)
                    || (departmentId != null && departmentId <= 0))
                throw new BizException(40300, "机构、科室或操作人上下文无效");
        }
    }

    public static Scope require() {
        Scope scope = CURRENT.get();
        if (scope == null) throw new BizException(40300, "缺少机构上下文，操作已拒绝");
        return scope;
    }
    public static java.util.Optional<Scope> current() { return java.util.Optional.ofNullable(CURRENT.get()); }

    /** System work is explicitly bound to one institution, without inventing a human operator. */
    public static Scope jobScope(long institutionId) {
        return new Scope(institutionId, null, null, "SYSTEM_JOB");
    }

    public static Long requireUserActor() {
        Long actor = require().actorId();
        if (actor == null) throw new BizException(40300, "该操作需要真实登录用户");
        return actor;
    }

    /** Policy only: the request resolver must validate institution existence/status and audit before installing it. */
    public static Scope selectInstitution(long institutionId) {
        Scope current = require();
        if (institutionId == current.institutionId()) return current;
        if (!"SYSTEM_ADMIN".equals(current.role()))
            throw new BizException(40300, "普通用户不能切换机构");
        // A department from the old institution must never be carried into the selected institution.
        return new Scope(institutionId, null, current.actorId(), current.role());
    }

    public static <T> T within(Scope scope, Supplier<T> operation) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(operation, "operation");
        Scope previous = CURRENT.get();
        CURRENT.set(scope);
        try { return operation.get(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
