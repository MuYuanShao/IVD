package com.zlth.ivd.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.finance.entity.DailyInventorySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyInventorySnapshotMapper extends BaseMapper<DailyInventorySnapshot> {

    /** 聚合 reagent_batch 当前库存快照 — 用于生成日结 */
    @Select("SELECT rb.reagent_id as reagentId, rb.warehouse_id as warehouseId, " +
            "COALESCE(SUM(rb.stock), 0) as closing " +
            "FROM reagent_batch rb WHERE rb.stock > 0 " +
            "GROUP BY rb.reagent_id, rb.warehouse_id")
    List<Map<String, Object>> aggregateCurrentStock();

    /** 当日入库金额 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM consumption_record " +
            "WHERE record_type = 'INBOUND' AND DATE(occurred_at) = #{date}")
    java.math.BigDecimal getTodayInbound(@Param("date") LocalDate date);

    /** 当日出库金额（消耗 + 调拨） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM consumption_record " +
            "WHERE record_type IN ('CONSUME', 'TRANSFER') AND DATE(occurred_at) = #{date}")
    java.math.BigDecimal getTodayOutbound(@Param("date") LocalDate date);

    /** 期初库存金额（上一日日结期末合计） */
    @Select("SELECT COALESCE(SUM(closing), 0) FROM daily_inventory_snapshot " +
            "WHERE snapshot_date = #{date}")
    java.math.BigDecimal getOpeningBalance(@Param("date") LocalDate date);

    /** 进销存汇总（日期范围内） */
    @Select("SELECT COALESCE(SUM(opening), 0) as totalOpening, " +
            "COALESCE(SUM(inbound), 0) as totalInbound, " +
            "COALESCE(SUM(outbound), 0) as totalOutbound, " +
            "COALESCE(SUM(adjustment), 0) as totalAdjustment, " +
            "COALESCE(SUM(closing), 0) as totalClosing, " +
            "COUNT(*) as reagentCount, " +
            "SUM(CASE WHEN is_balanced = 0 THEN 1 ELSE 0 END) as unbalancedCount " +
            "FROM daily_inventory_snapshot " +
            "WHERE snapshot_date >= #{startDate} AND snapshot_date <= #{endDate}")
    Map<String, Object> getBalanceAggregation(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
