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

    @Select("SELECT id FROM warehouse ORDER BY id FOR UPDATE")
    List<Long> lockWarehouses();

    @Select("SELECT (SELECT COUNT(*) FROM reagent_batch WHERE stock<>0 AND unit_price IS NULL AND avg_cost IS NULL) + " +
            "(SELECT COUNT(*) FROM consumption_record WHERE amount IS NULL)")
    long countUnpricedEvidence();

    @Select("SELECT DATE(MIN(effective_at)) FROM inventory_opening_balance")
    LocalDate getEarliestSupportedDate();

    /** Opening-import evidence defines a cutoff; older demo transactions remain preserved but are not replayed. */
    @Select("WITH cutoffs AS (SELECT reagent_id,batch_no,MIN(effective_at) cutoff FROM inventory_opening_balance GROUP BY reagent_id,batch_no), " +
            "raw AS (SELECT cr.* FROM consumption_record cr LEFT JOIN cutoffs c ON c.reagent_id=cr.reagent_id AND c.batch_no=cr.batch_no COLLATE utf8mb4_unicode_ci " +
            "WHERE c.cutoff IS NULL OR cr.occurred_at>=c.cutoff), " +
            "movements AS (" +
            "SELECT reagent_id,warehouse_id,occurred_at,amount inbound,0 outbound FROM raw WHERE record_type='INBOUND' " +
            "UNION ALL SELECT reagent_id,warehouse_id,occurred_at,0,amount FROM raw WHERE record_type='CONSUME' " +
            "UNION ALL SELECT reagent_id,from_warehouse_id,occurred_at,0,amount FROM raw WHERE record_type='TRANSFER' " +
            "UNION ALL SELECT cr.reagent_id,cr.warehouse_id,r.received_at,cr.amount,0 FROM raw cr " +
            "JOIN requisition_transfer_receipt r ON r.transfer_record_id=cr.id WHERE cr.record_type='TRANSFER' " +
            "UNION ALL SELECT reagent_id,warehouse_id,occurred_at,amount,0 FROM raw WHERE record_type='TRANSFER' AND requisition_id IS NULL), " +
            "openings AS (SELECT reagent_id,warehouse_id," +
            "SUM(CASE WHEN effective_at<DATE_ADD(#{date},INTERVAL 1 DAY) THEN amount ELSE 0 END) amount," +
            "SUM(CASE WHEN DATE(effective_at)=#{date} THEN amount ELSE 0 END) added," +
            "SUM(CASE WHEN effective_at>=DATE_ADD(#{date},INTERVAL 1 DAY) THEN amount ELSE 0 END) future " +
            "FROM inventory_opening_balance GROUP BY reagent_id,warehouse_id), " +
            "grains AS (SELECT reagent_id,warehouse_id FROM reagent_batch UNION SELECT reagent_id,warehouse_id FROM movements " +
            "UNION SELECT reagent_id,warehouse_id FROM openings), " +
            "stock AS (SELECT reagent_id,warehouse_id,SUM(stock*COALESCE(unit_price,avg_cost,0)) amount FROM reagent_batch GROUP BY reagent_id,warehouse_id), " +
            "ledger AS (SELECT reagent_id,warehouse_id," +
            "SUM(CASE WHEN occurred_at<#{date} THEN inbound-outbound ELSE 0 END) opening," +
            "SUM(CASE WHEN DATE(occurred_at)=#{date} THEN inbound ELSE 0 END) inbound," +
            "SUM(CASE WHEN DATE(occurred_at)=#{date} THEN outbound ELSE 0 END) outbound," +
            "SUM(CASE WHEN occurred_at>=DATE_ADD(#{date},INTERVAL 1 DAY) THEN inbound-outbound ELSE 0 END) future " +
            "FROM movements GROUP BY reagent_id,warehouse_id) " +
            "SELECT g.reagent_id reagentId,g.warehouse_id warehouseId," +
            "COALESCE(previous.closing+COALESCE(o.added,0),COALESCE(o.amount,0)+COALESCE(l.opening,0)) opening," +
            "COALESCE(l.inbound,0) inbound,COALESCE(l.outbound,0) outbound," +
            "COALESCE(s.amount,0)-COALESCE(l.future,0)-COALESCE(o.future,0) closing " +
            "FROM grains g LEFT JOIN stock s ON s.reagent_id=g.reagent_id AND s.warehouse_id=g.warehouse_id " +
            "LEFT JOIN ledger l ON l.reagent_id=g.reagent_id AND l.warehouse_id=g.warehouse_id " +
            "LEFT JOIN openings o ON o.reagent_id=g.reagent_id AND o.warehouse_id=g.warehouse_id " +
            "LEFT JOIN daily_inventory_snapshot previous ON previous.reagent_id=g.reagent_id AND previous.warehouse_id=g.warehouse_id " +
            "AND previous.snapshot_date=DATE_SUB(#{date},INTERVAL 1 DAY) WHERE g.warehouse_id IS NOT NULL")
    List<Map<String, Object>> getDailyBalances(@Param("date") LocalDate date);

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
