package com.zlth.ivd.system.service.impl;

import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.mapper.AuthenticationDirectoryMapper;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class AuthenticationDirectoryServiceImpl implements AuthenticationDirectoryService {
    @Override public boolean isActiveOwned(Long id,long owner){var row=activeDepartment(id);return row!=null && row.get("institution_id") instanceof Number value && value.longValue()==owner;}
    private final AuthenticationDirectoryMapper mapper;
    public SysUser findForLogin(String username) { return mapper.findForLogin(username); }
    public SysUser findActiveUser(Long id) { return mapper.findActiveUser(id); }
    public Map<String,Object> activeInstitution(Long id) { return mapper.activeInstitution(id); }
    public Map<String,Object> activeDepartment(Long id) { return mapper.activeDepartment(id); }
    public List<Long> activeInstitutionIds() { return mapper.activeInstitutionIds(); }
}
