package com.zlth.ivd.analytics.mapper;

import com.zlth.ivd.analytics.vo.WorkbenchVO.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Reporting-owned, cross-domain read model. Does not call or mutate external module mappers. */
@Mapper
public interface WorkbenchMapper {
    String LEDGER_SCOPE = " (#{deptId} IS NULL OR EXISTS (SELECT 1 FROM warehouse w WHERE w.id = CASE WHEN r.record_type='TRANSFER' THEN r.from_warehouse_id ELSE r.warehouse_id END AND w.dept_id=#{deptId})) ";
    String BATCH_SCOPE = " (#{deptId} IS NULL OR EXISTS (SELECT 1 FROM warehouse w WHERE w.id=b.warehouse_id AND w.dept_id=#{deptId})) ";
    String TODO_FILTER = " r.status IN ('PENDING_REVIEW','REVIEWED','PICKING','PARTIAL_FULFILLED','SHIPPED','SHORTAGE_PENDING') AND (#{deptId} IS NULL OR r.from_dept_id=#{deptId}) ";
    String PRODUCT_FROM = " FROM reagent_batch b LEFT JOIN reagent_catalog c ON c.id=b.reagent_id LEFT JOIN warehouse w ON w.id=b.warehouse_id WHERE b.stock>0 AND " + BATCH_SCOPE
            + " AND (#{keyword}='' OR LOCATE(#{keyword},c.generic_name)>0 OR LOCATE(#{keyword},c.manufacturer)>0 OR LOCATE(#{keyword},c.his_code)>0 OR LOCATE(#{keyword},b.batch_no)>0) ";

    @Select("SELECT dept_id,role FROM sys_user WHERE id=#{userId} AND status=1 AND is_deleted=0")
    ScopeUser userScope(@Param("userId") Long userId);

    @Select("SELECT CASE WHEN SUM(CASE WHEN r.record_type='CONSUME' AND r.amount IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(CASE WHEN r.record_type='CONSUME' THEN r.amount ELSE 0 END),0) END consumption_amount, "
            + "COALESCE(SUM(CASE WHEN r.record_type='CONSUME' THEN 1 ELSE 0 END),0) consumed_records, COALESCE(SUM(CASE WHEN r.record_type='INBOUND' THEN 1 ELSE 0 END),0) inbound_records, COALESCE(SUM(CASE WHEN r.record_type='TRANSFER' THEN 1 ELSE 0 END),0) transfer_records, COALESCE(SUM(CASE WHEN r.amount IS NULL THEN 1 ELSE 0 END),0) unpriced_records "
            + "FROM consumption_record r WHERE r.occurred_at>=#{start} AND r.occurred_at<#{end} AND " + LEDGER_SCOPE)
    Metrics metrics(@Param("deptId") Long deptId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT CAST(r.occurred_at AS DATE) date, "
            + "CASE WHEN SUM(CASE WHEN r.record_type='INBOUND' AND r.amount IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(CASE WHEN r.record_type='INBOUND' THEN r.amount ELSE 0 END),0) END inbound, "
            + "CASE WHEN SUM(CASE WHEN r.record_type='TRANSFER' AND r.amount IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(CASE WHEN r.record_type='TRANSFER' THEN r.amount ELSE 0 END),0) END outbound, "
            + "SUM(CASE WHEN r.amount IS NULL THEN 1 ELSE 0 END) unpriced_records FROM consumption_record r WHERE r.record_type IN ('INBOUND','TRANSFER') AND r.occurred_at>=#{start} AND r.occurred_at<#{end} AND " + LEDGER_SCOPE + " GROUP BY CAST(r.occurred_at AS DATE) ORDER BY date")
    List<Trend> trend(@Param("deptId") Long deptId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(CASE WHEN b.expiry_date<#{today} THEN 1 ELSE 0 END),0) expired_batches, COALESCE(SUM(CASE WHEN b.expiry_date>=#{today} AND b.expiry_date<=#{until} THEN 1 ELSE 0 END),0) expiring_batches, COALESCE(SUM(CASE WHEN b.udi_status='PENDING_UDI' THEN 1 ELSE 0 END),0) pending_udi_batches FROM reagent_batch b WHERE b.stock>0 AND " + BATCH_SCOPE)
    Alerts alerts(@Param("deptId") Long deptId, @Param("today") LocalDate today, @Param("until") LocalDate until);

    @Select("SELECT COUNT(*) FROM shortage_order WHERE status IN ('PENDING_PROCUREMENT','PURCHASING') AND (#{deptId} IS NULL OR from_dept_id=#{deptId})")
    long shortages(@Param("deptId") Long deptId);

    @Select("SELECT COUNT(*) FROM purchase_requisition r WHERE " + TODO_FILTER)
    long todoCount(@Param("deptId") Long deptId);

    @Select("SELECT TRIM(CAST(r.id AS CHAR(20))) id,r.req_no,r.status,r.created_at,COUNT(i.id) line_count, "
            + "COALESCE(SUM(CASE WHEN i.qty_requested>0 AND i.qty_fulfilled>=i.qty_requested THEN 1 ELSE 0 END),0) fulfilled_lines, "
            + "CASE WHEN SUM(CASE WHEN i.id IS NOT NULL AND i.amount IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(i.amount),0) END amount "
            + "FROM purchase_requisition r LEFT JOIN purchase_requisition_item i ON i.req_id=r.id WHERE " + TODO_FILTER
            + " GROUP BY r.id,r.req_no,r.status,r.created_at ORDER BY r.created_at,r.id LIMIT 6")
    List<Todo> todos(@Param("deptId") Long deptId);

    @Select("SELECT COUNT(*) " + PRODUCT_FROM)
    long productCount(@Param("deptId") Long deptId, @Param("keyword") String keyword);

    @Select("SELECT TRIM(CAST(b.id AS CHAR(20))) id,TRIM(CAST(b.reagent_id AS CHAR(20))) reagent_id,c.his_code code,COALESCE(c.generic_name,'目录信息缺失') name,c.manufacturer,c.specification,b.unit,b.stock quantity,b.expiry_date expiry,b.batch_no batch,b.location_code location,w.name warehouse "
            + PRODUCT_FROM + "ORDER BY b.expiry_date IS NULL,b.expiry_date,b.id LIMIT #{limit} OFFSET #{offset}")
    List<Product> products(@Param("deptId") Long deptId, @Param("keyword") String keyword, @Param("offset") long offset, @Param("limit") int limit);
}
