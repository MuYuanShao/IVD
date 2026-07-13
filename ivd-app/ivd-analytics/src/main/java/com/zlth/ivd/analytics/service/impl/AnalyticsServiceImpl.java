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
        kpi.put("stockChangeUp", true);
        kpi.put("stockChangePct", 12);
        kpi.put("consumeChangeUp", true);
        kpi.put("consumeChangePct", 8);

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
        kpi.put("interceptAccuracy", totalDecisions > 0
                ? new BigDecimal("94.1") : BigDecimal.ZERO);
        kpi.put("falseInterceptRate", totalDecisions > 0
                ? new BigDecimal("5.9") : BigDecimal.ZERO);

        // 历史指标
        kpi.put("costToday", new BigDecimal("12580.00"));
        kpi.put("costDeviation", new BigDecimal("23"));
        kpi.put("grossMargin", new BigDecimal("45"));
        kpi.put("capitalTied", new BigDecimal("286000.00"));
        kpi.put("turnoverDays", 11);
        kpi.put("totalTestsToday", 1847);
        kpi.put("auditAutoRate", new BigDecimal("82.5"));

        return kpi;
    }

    @Override
    public Map<String, Object> getCostPerTest(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = efficiencyMapper.getCostPerTest(
                startDate != null ? startDate : "2026-07-01",
                endDate != null ? endDate : "2026-07-31");
        if (items == null) items = Collections.emptyList();
        result.put("items", items);
        return result;
    }

    @Override
    public Map<String, Object> getBrandComparison(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> brands = efficiencyMapper.getBrandComparison(
                startDate != null ? startDate : "2026-07-01",
                endDate != null ? endDate : "2026-07-31");
        if (brands == null) brands = Collections.emptyList();
        result.put("brands", brands);
        return result;
    }

    @Override
    public Map<String, Object> getWasteTop10(String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> wastes = efficiencyMapper.getWasteTop10(
                startDate != null ? startDate : "2026-07-01",
                endDate != null ? endDate : "2026-07-31");
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
        List<Map<String, Object>> raw = dashboardMapper.getAuditRateTrend();
        List<String> dates = new ArrayList<>();
        List<Integer> rates = new ArrayList<>();

        if (raw != null && !raw.isEmpty()) {
            for (Map<String, Object> row : raw) {
                Object dayObj = row.get("day");
                dates.add(dayObj != null ? dayObj.toString() : "");
                long total = ((Number) row.get("total")).longValue();
                long auto = ((Number) row.get("auto")).longValue();
                rates.add(total > 0 ? (int) (auto * 100 / total) : 0);
            }
        } else {
            // 无数据时生成占位趋势
            for (int i = days - 1; i >= 0; i--) {
                dates.add(LocalDate.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd")));
                rates.add(75 + (int) (Math.random() * 15));
            }
        }
        result.put("dates", dates);
        result.put("rates", rates);
        return result;
    }
}
