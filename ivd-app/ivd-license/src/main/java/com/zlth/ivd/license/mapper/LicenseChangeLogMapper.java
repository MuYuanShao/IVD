package com.zlth.ivd.license.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.vo.ChangeLogVO;
import com.zlth.ivd.license.entity.LicenseChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LicenseChangeLogMapper extends BaseMapper<LicenseChangeLog> {

    /** 按证照 ID 查询变更历史（时间倒序） */
    @Select("SELECT * FROM license_change_log WHERE license_id = #{licenseId} ORDER BY created_at DESC")
    List<LicenseChangeLog> findByLicenseId(Long licenseId);

    /**
     * 分页查询所有变更记录（联表证照 + 供应商）
     * MyBatis-Plus 分页插件自动拦截 COUNT + LIMIT
     */
    @Select("""
        <script>
        SELECT lcl.id, lcl.license_id, lcl.change_type, lcl.old_expire_date, lcl.new_expire_date,
               lcl.operator_name, lcl.note, lcl.created_at,
               sl.cert_no, sl.license_type, s.name as supplier_name
        FROM license_change_log lcl
        LEFT JOIN supplier_license sl ON lcl.license_id = sl.id
        LEFT JOIN supplier s ON sl.supplier_id = s.id
        <where>
            <if test='query.changeType != null and query.changeType != ""'>
                AND lcl.change_type = #{query.changeType}
            </if>
            <if test='query.startDate != null and query.startDate != ""'>
                AND lcl.created_at &gt;= #{query.startDate}
            </if>
            <if test='query.endDate != null and query.endDate != ""'>
                AND lcl.created_at &lt;= #{query.endDate}
            </if>
            <if test='query.keyword != null and query.keyword != ""'>
                AND (s.name LIKE CONCAT('%',#{query.keyword},'%')
                     OR sl.cert_no LIKE CONCAT('%',#{query.keyword},'%'))
            </if>
        </where>
        ORDER BY lcl.created_at DESC
        </script>
    """)
    IPage<ChangeLogVO> findAllWithLicense(Page<ChangeLogVO> page, @Param("query") ChangeLogQuery query);
}
