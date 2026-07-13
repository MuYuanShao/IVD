package com.zlth.ivd.finance.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.finance.entity.DailyInventorySnapshot;
import com.zlth.ivd.finance.mapper.DailyInventorySnapshotMapper;
import com.zlth.ivd.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class FinanceServiceImpl implements FinanceService {

    private final DailyInventorySnapshotMapper snapshotMapper;

    @Override
    public PageVO<DailyInventorySnapshot> listSnapshots(int page, int pageSize, LocalDate startDate, LocalDate endDate, Long reagentId) {
        LambdaQueryWrapper<DailyInventorySnapshot> qw = new LambdaQueryWrapper<>();
        if (startDate != null) qw.ge(DailyInventorySnapshot::getSnapshotDate, startDate);
        if (endDate != null) qw.le(DailyInventorySnapshot::getSnapshotDate, endDate);
        if (reagentId != null) qw.eq(DailyInventorySnapshot::getReagentId, reagentId);
        qw.orderByDesc(DailyInventorySnapshot::getSnapshotDate);
        Page<DailyInventorySnapshot> p = snapshotMapper.selectPage(new Page<>(page, pageSize), qw);
        return new PageVO<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    public void dailySettlement(LocalDate date) {
        LocalDate settleDate = date != null ? date : LocalDate.now();
        log.info("执行日结: date={}", settleDate);

        // 1. 查询前一日期末作为期初
        BigDecimal opening = snapshotMapper.getOpeningBalance(settleDate.minusDays(1));
        if (opening == null) opening = BigDecimal.ZERO;

        // 2. 当日入库和出库
        BigDecimal inbound = snapshotMapper.getTodayInbound(settleDate);
        if (inbound == null) inbound = BigDecimal.ZERO;
        BigDecimal outbound = snapshotMapper.getTodayOutbound(settleDate);
        if (outbound == null) outbound = BigDecimal.ZERO;

        // 3. 从 reagent_batch 聚合当前库存
        List<Map<String, Object>> stocks = snapshotMapper.aggregateCurrentStock();
        if (stocks != null && !stocks.isEmpty()) {
            for (Map<String, Object> row : stocks) {
                DailyInventorySnapshot snap = new DailyInventorySnapshot();
                snap.setSnapshotDate(settleDate);
                snap.setReagentId(row.get("reagentId") != null ? ((Number) row.get("reagentId")).longValue() : null);
                snap.setWarehouseId(row.get("warehouseId") != null ? ((Number) row.get("warehouseId")).longValue() : null);
                BigDecimal closing = row.get("closing") != null ? new BigDecimal(row.get("closing").toString()) : BigDecimal.ZERO;
                snap.setOpening(opening);
                snap.setInbound(inbound);
                snap.setOutbound(outbound);
                snap.setAdjustment(BigDecimal.ZERO);
                snap.setClosing(closing);
                // 差异 = 期初 + 入库 - 出库 + 调整 - 期末
                BigDecimal expected = opening.add(inbound).subtract(outbound).add(snap.getAdjustment());
                BigDecimal diff = expected.subtract(closing).abs();
                snap.setDifference(diff);
                snap.setDifferenceRatio(closing.compareTo(BigDecimal.ZERO) > 0
                        ? diff.divide(closing, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                snap.setIsBalanced(snap.getDifferenceRatio().compareTo(new BigDecimal("0.05")) <= 0 ? 1 : 0);
                snapshotMapper.insert(snap);
            }
        } else {
            // 无库存数据时插入汇总快照
            DailyInventorySnapshot snap = new DailyInventorySnapshot();
            snap.setSnapshotDate(settleDate);
            snap.setOpening(opening);
            snap.setInbound(inbound);
            snap.setOutbound(outbound);
            snap.setAdjustment(BigDecimal.ZERO);
            BigDecimal closing = opening.add(inbound).subtract(outbound);
            snap.setClosing(closing);
            snap.setDifference(BigDecimal.ZERO);
            snap.setDifferenceRatio(BigDecimal.ZERO);
            snap.setIsBalanced(1);
            snapshotMapper.insert(snap);
        }
        log.info("日结完成: date={}, 入库={}, 出库={}", settleDate, inbound, outbound);
    }

    @Override
    public void monthlySettlement(String yearMonth) {
        log.info("执行月结: yearMonth={}", yearMonth);
        if (yearMonth == null || yearMonth.length() < 7) return;
        LocalDate start = LocalDate.parse(yearMonth + "-01");
        LocalDate end = start.plusMonths(1).minusDays(1);
        Map<String, Object> agg = snapshotMapper.getBalanceAggregation(start, end);
        log.info("月结完成: {} → {}, 汇总: {}", start, end, agg);
    }

    @Override
    public Map<String, Object> getSnapshotStats(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("snapshotDate", d.toString());
        stats.put("totalReagents", snapshotMapper.selectCount(
            new LambdaQueryWrapper<DailyInventorySnapshot>().eq(DailyInventorySnapshot::getSnapshotDate, d)
        ));
        stats.put("balancedCount", snapshotMapper.selectCount(
            new LambdaQueryWrapper<DailyInventorySnapshot>()
                .eq(DailyInventorySnapshot::getSnapshotDate, d)
                .eq(DailyInventorySnapshot::getIsBalanced, 1)
        ));
        return stats;
    }

    @Override
    public Map<String, Object> getBalanceSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startDate", start.toString());
        summary.put("endDate", end.toString());

        Map<String, Object> agg = snapshotMapper.getBalanceAggregation(start, end);
        if (agg != null && agg.get("totalClosing") != null) {
            summary.put("totalOpening", agg.get("totalOpening") != null ? agg.get("totalOpening") : BigDecimal.ZERO);
            summary.put("totalInbound", agg.get("totalInbound") != null ? agg.get("totalInbound") : BigDecimal.ZERO);
            summary.put("totalOutbound", agg.get("totalOutbound") != null ? agg.get("totalOutbound") : BigDecimal.ZERO);
            summary.put("totalClosing", agg.get("totalClosing") != null ? agg.get("totalClosing") : BigDecimal.ZERO);
            long unbalanced = agg.get("unbalancedCount") != null ? ((Number) agg.get("unbalancedCount")).longValue() : 0;
            long total = agg.get("reagentCount") != null ? ((Number) agg.get("reagentCount")).longValue() : 1;
            summary.put("differenceRatio", total > 0
                    ? new BigDecimal(unbalanced).divide(new BigDecimal(total), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        } else {
            // 无快照数据时返回合理默认值
            summary.put("totalOpening", BigDecimal.ZERO);
            summary.put("totalInbound", BigDecimal.ZERO);
            summary.put("totalOutbound", BigDecimal.ZERO);
            summary.put("totalClosing", BigDecimal.ZERO);
            summary.put("differenceRatio", BigDecimal.ZERO);
        }
        return summary;
    }
}
