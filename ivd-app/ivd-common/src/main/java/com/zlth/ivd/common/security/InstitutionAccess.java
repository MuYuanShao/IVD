package com.zlth.ivd.common.security;

import com.zlth.ivd.common.exception.BizException;
import java.util.Objects;

/** Resource ownership checks supplement SQL filtering; they never grant a business role. */
public final class InstitutionAccess {
    private InstitutionAccess() { }

    public static void requireOwned(Long institutionId, Long departmentId, boolean departmentRestricted) {
        var scope = InstitutionContext.require();
        if (!Objects.equals(institutionId, scope.institutionId()))
            throw new BizException(40400, "资源不存在或无权访问");
        if (departmentRestricted) {
            if (scope.departmentId() == null)
                throw new BizException(40300, "操作需要有效的科室上下文");
            if (!Objects.equals(departmentId, scope.departmentId()))
                throw new BizException(40400, "资源不存在或无权访问");
        }
    }
}
