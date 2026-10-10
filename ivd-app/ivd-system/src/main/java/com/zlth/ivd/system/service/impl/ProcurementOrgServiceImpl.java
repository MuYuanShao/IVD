package com.zlth.ivd.system.service.impl;

import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.mapper.SysDeptMapper;
import com.zlth.ivd.system.mapper.SysUserMapper;
import com.zlth.ivd.system.service.ProcurementOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcurementOrgServiceImpl implements ProcurementOrgService {
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    public SysDept findDept(Long id) { return deptMapper.selectById(id); }
    public SysUser findUser(Long id) { return userMapper.selectById(id); }
    public List<SysDept> findDepts(Collection<Long> ids) { return ids == null || ids.isEmpty() ? List.of() : deptMapper.selectBatchIds(ids); }
    public List<SysUser> findUsers(Collection<Long> ids) { return ids == null || ids.isEmpty() ? List.of() : userMapper.selectBatchIds(ids); }
}
