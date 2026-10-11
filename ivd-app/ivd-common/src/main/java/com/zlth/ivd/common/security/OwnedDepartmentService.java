package com.zlth.ivd.common.security;
/** Directory service port: inventory validates departments without a reverse module dependency. */
public interface OwnedDepartmentService {
    boolean isActiveOwned(Long departmentId,long institutionId);
}
