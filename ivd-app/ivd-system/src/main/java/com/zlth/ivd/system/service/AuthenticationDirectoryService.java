package com.zlth.ivd.system.service;

import com.zlth.ivd.system.entity.SysUser;
import java.util.List;
import java.util.Map;

public interface AuthenticationDirectoryService {
    SysUser findForLogin(String username);
    SysUser findActiveUser(Long userId);
    Map<String,Object> activeInstitution(Long institutionId);
    Map<String,Object> activeDepartment(Long departmentId);
    List<Long> activeInstitutionIds();
}
