package com.zlth.ivd.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.zlth.ivd.system.entity.SysUser;
import org.apache.ibatis.annotations.*;
import java.util.Map;
import java.util.List;

/** Only fixed authentication/bootstrap lookups bypass tenant SQL; never a general CRUD mapper. */
@Mapper
public interface AuthenticationDirectoryMapper {
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT * FROM sys_user WHERE username=#{username} AND status=1 AND is_deleted=0")
    SysUser findForLogin(@Param("username") String username);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT * FROM sys_user WHERE id=#{id} AND status=1 AND is_deleted=0")
    SysUser findActiveUser(@Param("id") Long id);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT id,name,type,status FROM institution WHERE id=#{id} AND status=1 AND type='INTERNAL'")
    Map<String,Object> activeInstitution(@Param("id") Long id);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT id,institution_id,status FROM sys_dept WHERE id=#{id} AND is_deleted=0 AND status=1")
    Map<String,Object> activeDepartment(@Param("id") Long id);
    @InterceptorIgnore(tenantLine="true")
    @Select("SELECT id FROM institution WHERE status=1 AND type='INTERNAL' ORDER BY id")
    List<Long> activeInstitutionIds();
}
