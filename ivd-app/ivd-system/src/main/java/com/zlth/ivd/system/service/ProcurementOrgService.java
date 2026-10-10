package com.zlth.ivd.system.service;

import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.entity.SysUser;
import java.util.Collection;
import java.util.List;

/** 采购模块可使用的组织只读契约。 */
public interface ProcurementOrgService {
    SysDept findDept(Long id);
    SysUser findUser(Long id);
    List<SysDept> findDepts(Collection<Long> ids);
    List<SysUser> findUsers(Collection<Long> ids);
}
