package com.zlth.ivd.license.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.license.entity.SupplierLicense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SupplierLicenseMapper extends BaseMapper<SupplierLicense> {

    /** 按状态统计证照数量 */
    @Select("SELECT status, COUNT(*) as cnt FROM supplier_license GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /** 查询近 N 天内到期的证照 */
    @Select("SELECT * FROM supplier_license WHERE status IN ('ACTIVE','EXPIRING') AND expire_date <= DATE_ADD(CURDATE(), INTERVAL #{days} DAY) AND expire_date >= CURDATE()")
    List<SupplierLicense> findExpiringWithinDays(int days);
}
