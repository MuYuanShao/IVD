package com.zlth.ivd.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 效益分析聚合查询 Mapper
 */
@Mapper
public interface EfficiencyMapper {

    /** 单测试成本表 */
    @Select("SELECT cr.reagent_id as reagentId, rc.generic_name as testItem, " +
            "CASE WHEN COUNT(cr.unit_price)=COUNT(*) THEN AVG(cr.unit_price) END as reagentCost, " +
            "CASE WHEN COUNT(cr.amount)=COUNT(*) THEN SUM(cr.amount) END as totalCost, " +
            "SUM(CASE WHEN cr.unit_price IS NULL OR cr.amount IS NULL THEN 1 ELSE 0 END) as missingPriceCount, " +
            "COUNT(*) as count " +
            "FROM consumption_record cr " +
            "LEFT JOIN reagent_catalog rc ON rc.id = cr.reagent_id " +
            "WHERE cr.record_type = 'CONSUME' AND cr.occurred_at >= #{startDate} AND cr.occurred_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) " +
            "GROUP BY cr.reagent_id, rc.generic_name ORDER BY totalCost DESC LIMIT 20")
    List<Map<String, Object>> getCostPerTest(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 品牌成本对比 */
    @Select("SELECT s.name as brand, " +
            "CASE WHEN COUNT(cr.unit_price)=COUNT(*) THEN AVG(cr.unit_price) END as avgPrice, " +
            "COUNT(*) as testCount, " +
            "CASE WHEN COUNT(cr.amount)=COUNT(*) THEN SUM(cr.amount) END as totalCost, " +
            "SUM(CASE WHEN cr.unit_price IS NULL OR cr.amount IS NULL THEN 1 ELSE 0 END) as missingPriceCount " +
            "FROM consumption_record cr " +
            "LEFT JOIN reagent_supplier_rel rsr ON rsr.reagent_id = cr.reagent_id AND rsr.is_default = 1 " +
            "LEFT JOIN supplier s ON s.id = rsr.supplier_id " +
            "WHERE cr.record_type = 'CONSUME' AND cr.occurred_at >= #{startDate} AND cr.occurred_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) " +
            "GROUP BY s.id, s.name ORDER BY totalCost DESC")
    List<Map<String, Object>> getBrandComparison(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** 浪费点 Top10 */
    @Select("SELECT rc.generic_name as reagent, cr.purpose as wasteType, " +
            "CASE WHEN COUNT(cr.amount)=COUNT(*) THEN SUM(cr.amount) END as amount, COUNT(*) as qty, " +
            "SUM(CASE WHEN cr.amount IS NULL THEN 1 ELSE 0 END) as missingPriceCount " +
            "FROM consumption_record cr " +
            "LEFT JOIN reagent_catalog rc ON rc.id = cr.reagent_id " +
            "WHERE cr.purpose IN ('EXPIRED','OPEN_EXPIRED','RETEST','QC_LOSS','DEPT_USE') " +
            "AND cr.occurred_at >= #{startDate} AND cr.occurred_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) " +
            "GROUP BY cr.reagent_id, rc.generic_name, cr.purpose ORDER BY amount DESC LIMIT 10")
    List<Map<String, Object>> getWasteTop10(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
