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

        if (settleDate.isAfter(LocalDate.now()))
            throw new com.zlth.ivd.common.exception.BizException(40001, "不能生成未来日期的日结");
        snapshotMapper.lockWarehouses();
        LocalDate supportedFrom = snapshotMapper.getEarliestSupportedDate();
        if (supportedFrom != null && settleDate.isBefore(supportedFrom))
            throw new com.zlth.ivd.common.exception.BizException(40001, "初始化前的历史证据不完整，不能日结；支持日期自" + supportedFrom);
        if (snapshotMapper.countUnpricedEvidence() > 0)
            throw new com.zlth.ivd.common.exception.BizException(40001, "库存或流水存在未定价记录，请先核对金额后日结");
        if (snapshotMapper.selectCount(new LambdaQueryWrapper<DailyInventorySnapshot>()
                .eq(DailyInventorySnapshot::getSnapshotDate, settleDate)) > 0)
            throw new com.zlth.ivd.common.exception.BizException(40900, "该日期已经日结，请先核对已有快照");
        List<Map<String, Object>> balances = snapshotMapper.getDailyBalances(settleDate);
        if (balances == null) return;
        for (Map<String, Object> row : balances) {
            DailyInventorySnapshot snap = new DailyInventorySnapshot();
            snap.setSnapshotDate(settleDate);
            snap.setReagentId(((Number) row.get("reagentId")).longValue());
            snap.setWarehouseId(((Number) row.get("warehouseId")).longValue());
            snap.setOpening(decimal(row, "opening"));
            snap.setInbound(decimal(row, "inbound"));
            snap.setOutbound(decimal(row, "outbound"));
            snap.setAdjustment(BigDecimal.ZERO);
            snap.setClosing(decimal(row, "closing"));
            BigDecimal expected = snap.getOpening().add(snap.getInbound()).subtract(snap.getOutbound());
            BigDecimal difference = expected.subtract(snap.getClosing()).abs();
            snap.setDifference(difference);
            BigDecimal denominator = expected.abs().max(snap.getClosing().abs());
            snap.setDifferenceRatio(denominator.signum() == 0 ? BigDecimal.ZERO
                    : difference.divide(denominator, 4, RoundingMode.HALF_UP));
            snap.setIsBalanced(snap.getDifferenceRatio().compareTo(new BigDecimal("0.01")) < 0 ? 1 : 0);
            snapshotMapper.insert(snap);
        }
        log.info("日结完成: date={}, grains={}", settleDate, balances.size());
    }

    private static BigDecimal decimal(Map<String, Object> row, String field) {
        Object value = row.get(field);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
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
