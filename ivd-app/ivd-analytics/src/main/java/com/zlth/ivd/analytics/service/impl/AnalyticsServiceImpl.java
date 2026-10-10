package com.zlth.ivd.analytics.service.impl;

import com.zlth.ivd.analytics.mapper.DashboardMapper;
import com.zlth.ivd.analytics.mapper.EfficiencyMapper;
import com.zlth.ivd.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final DashboardMapper dashboardMapper;
    private final EfficiencyMapper efficiencyMapper;

    @Override
    public Map<String, Object> getDashboardKpi() {
        Map<String, Object> kpi = new LinkedHashMap<>();

        // 在库试剂量
        Long stockCount = dashboardMapper.countStock();
        kpi.put("stockCount", stockCount != null ? stockCount : 0);

        // 今日消耗
        Long consumeToday = dashboardMapper.getTodayConsumption();
        kpi.put("consumeToday", consumeToday != null ? consumeToday : 0);

        // 库存变化趋势
        kpi.put("stockChangeUp", null);
        kpi.put("stockChangePct", null);
        kpi.put("consumeChangeUp", null);
        kpi.put("consumeChangePct", null);

        // 近效期批次
        Long expiringBatches = dashboardMapper.countExpiringBatches();
        kpi.put("expiringBatches", expiringBatches != null ? expiringBatches : 0);

        // 待审核
        Long pendingAudit = dashboardMapper.countPendingAudit();
        kpi.put("pendingAudit", pendingAudit != null ? pendingAudit : 0);

        // 今日审核统计
        long autoReleased = 0, warnReleased = 0, manualReview = 0, rejectRetest = 0;
        List<Map<String, Object>> auditStats = dashboardMapper.getTodayAuditStats();
        if (auditStats != null) {
            for (Map<String, Object> row : auditStats) {
                String decision = (String) row.get("decision");
                long cnt = ((Number) row.get("cnt")).longValue();
                switch (decision) {
                    case "AUTO_RELEASE": autoReleased = cnt; break;
                    case "WARN_RELEASE": warnReleased = cnt; break;
                    case "HOLD_REVIEW": manualReview = cnt; break;
                    case "REJECT_RETEST": rejectRetest = cnt; break;
                }
            }
        }
        long totalDecisions = autoReleased + warnReleased + manualReview + rejectRetest;
        kpi.put("autoReleased", autoReleased);
        kpi.put("warnReleased", warnReleased);
        kpi.put("manualReview", manualReview);

        // 审核率计算
        kpi.put("autoAuditRate", totalDecisions > 0
                ? new BigDecimal(autoReleased).multiply(BigDecimal.valueOf(100)).divide(new BigDecimal(totalDecisions), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        // No adjudicated reference outcomes / LIS revenue integration: explicitly unavailable.
        kpi.put("interceptAccuracy", null);
        kpi.put("falseInterceptRate", null);
        kpi.put("costToday", dashboardMapper.getTodayConsumptionCost());
        kpi.put("capitalTied", dashboardMapper.getCurrentStockAmount());
        kpi.put("costDeviation", null);
        kpi.put("grossMargin", null);
        kpi.put("turnoverDays", null);
        kpi.put("totalTestsToday", null);
        kpi.put("auditAutoRate", kpi.get("autoAuditRate"));

        return kpi;
    }

    @Override
    public Map<String, Object> getCostPerTest(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = efficiencyMapper.getCostPerTest(
                startDate != null ? startDate : LocalDate.now().withDayOfMonth(1).toString(),
                endDate != null ? endDate : LocalDate.now().toString());
        if (items == null) items = Collections.emptyList();
        result.put("items", items);
        return result;
    }

    @Override
    public Map<String, Object> getBrandComparison(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> brands = efficiencyMapper.getBrandComparison(
                startDate != null ? startDate : LocalDate.now().withDayOfMonth(1).toString(),
                endDate != null ? endDate : LocalDate.now().toString());
        if (brands == null) brands = Collections.emptyList();
        result.put("brands", brands);
        return result;
    }

    @Override
    public Map<String, Object> getWasteTop10(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> wastes = efficiencyMapper.getWasteTop10(
                startDate != null ? startDate : LocalDate.now().withDayOfMonth(1).toString(),
                endDate != null ? endDate : LocalDate.now().toString());
        if (wastes == null) wastes = Collections.emptyList();
        // 添加排名
        int rank = 1;
        for (Map<String, Object> w : wastes) {
            w.put("rank", rank++);
        }
        result.put("wastes", wastes);
        return result;
    }

    @Override
    public Map<String, Object> getAuditRateTrend(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (days < 1 || days > 366) throw new com.zlth.ivd.common.exception.BizException(40001, "趋势天数必须为1至366");
        List<Map<String, Object>> raw = dashboardMapper.getAuditRateTrend(days);
        Map<String, Integer> daily = new HashMap<>();
        if (raw != null) for (Map<String, Object> row : raw) {
            long total = ((Number) row.get("total")).longValue();
            long auto = ((Number) row.get("auto")).longValue();
            daily.put(String.valueOf(row.get("day")), total > 0 ? (int) (auto * 100 / total) : null);
        }
        List<String> dates = new ArrayList<>();
        List<Integer> rates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dates.add(day.format(DateTimeFormatter.ofPattern("MM-dd")));
            rates.add(daily.get(day.toString())); // null produces a gap, not a fictional measurement.
        }
        result.put("dates", dates);
        result.put("rates", rates);
        return result;
    }
}
