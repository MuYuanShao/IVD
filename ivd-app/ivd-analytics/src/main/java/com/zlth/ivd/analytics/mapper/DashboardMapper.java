package com.zlth.ivd.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 驾驶舱 KPI 聚合查询 Mapper
 * 跨模块只读查询，不依赖其他模块的 Mapper
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT CASE WHEN SUM(CASE WHEN amount IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(amount),0) END FROM consumption_record WHERE record_type='CONSUME' AND DATE(occurred_at)=CURRENT_DATE")
    java.math.BigDecimal getTodayConsumptionCost();

    @Select("SELECT CASE WHEN SUM(CASE WHEN stock<>0 AND unit_price IS NULL AND avg_cost IS NULL THEN 1 ELSE 0 END)>0 THEN NULL ELSE COALESCE(SUM(stock*COALESCE(unit_price,avg_cost)),0) END FROM reagent_batch")
    java.math.BigDecimal getCurrentStockAmount();

    /** 在库试剂量（盒） */
    @Select("SELECT COALESCE(SUM(stock), 0) FROM reagent_batch WHERE stock > 0")
    Long countStock();

    /** 近效期 ≤30 天批次数 */
    @Select("SELECT COUNT(*) FROM reagent_batch WHERE stock > 0 AND expiry_date <= DATE_ADD(NOW(), INTERVAL 30 DAY)")
    Long countExpiringBatches();

    /** 今日消耗量（盒） */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM consumption_record WHERE record_type = 'CONSUME' AND DATE(occurred_at) = CURDATE()")
    Long getTodayConsumption();

    /** 本月消耗趋势（近30天按日统计） */
    @Select("SELECT DATE(occurred_at) as day, COALESCE(SUM(quantity), 0) as inbound, 0 as outbound " +
            "FROM consumption_record WHERE occurred_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(occurred_at) ORDER BY day")
    List<Map<String, Object>> getMonthlyTrend();

    /** 待审核结果数 */
    @Select("SELECT COUNT(*) FROM audit_decision_log WHERE decision IN ('HOLD_REVIEW','REJECT_RETEST') AND DATE(decided_at) = CURDATE()")
    Long countPendingAudit();

    /** 今日审核统计 */
    @Select("SELECT decision, COUNT(*) as cnt FROM audit_decision_log " +
            "WHERE DATE(decided_at) = CURDATE() GROUP BY decision")
    List<Map<String, Object>> getTodayAuditStats();

    /** 近30天自动审核率趋势 */
    @Select("SELECT DATE(decided_at) as day, " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN decision = 'AUTO_RELEASE' THEN 1 ELSE 0 END) as auto " +
            "FROM audit_decision_log " +
            "WHERE decided_at >= DATE_SUB(CURDATE(), INTERVAL (#{days}-1) DAY) AND decided_at < DATE_ADD(CURDATE(),INTERVAL 1 DAY) " +
            "GROUP BY DATE(decided_at) ORDER BY day")
    List<Map<String, Object>> getAuditRateTrend(@org.apache.ibatis.annotations.Param("days") int days);
}
