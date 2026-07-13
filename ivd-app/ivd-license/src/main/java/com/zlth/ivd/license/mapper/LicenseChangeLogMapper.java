package com.zlth.ivd.license.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.license.entity.LicenseChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LicenseChangeLogMapper extends BaseMapper<LicenseChangeLog> {

    /** 按证照 ID 查询变更历史（时间倒序） */
    @Select("SELECT * FROM license_change_log WHERE license_id = #{licenseId} ORDER BY created_at DESC")
    List<LicenseChangeLog> findByLicenseId(Long licenseId);
}
