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
    @Select("""
            SELECT CASE WHEN status='FROZEN' THEN 'FROZEN'
                        WHEN status='EXPIRED' THEN 'EXPIRED'
                        WHEN expire_date IS NULL OR expire_date<=CURRENT_DATE THEN 'EXPIRED'
                        WHEN expire_date<=DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY) THEN 'EXPIRING'
                        ELSE 'ACTIVE' END status,
                   COUNT(*) cnt
            FROM supplier_license
            GROUP BY CASE WHEN status='FROZEN' THEN 'FROZEN'
                          WHEN status='EXPIRED' THEN 'EXPIRED'
                          WHEN expire_date IS NULL OR expire_date<=CURRENT_DATE THEN 'EXPIRED'
                          WHEN expire_date<=DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY) THEN 'EXPIRING'
                          ELSE 'ACTIVE' END
            """)
    List<Map<String, Object>> countByStatus();

    /** Include overdue/frozen credentials so a polling integrator cannot miss a still-blocking certificate. */
    @Select("""
            SELECT * FROM supplier_license
            WHERE expire_date IS NULL OR status IN ('FROZEN','EXPIRED')
               OR expire_date <= DATE_ADD(CURRENT_DATE, INTERVAL #{days} DAY)
            ORDER BY expire_date, id
            """)
    List<SupplierLicense> findExpiringWithinDays(int days);
}
