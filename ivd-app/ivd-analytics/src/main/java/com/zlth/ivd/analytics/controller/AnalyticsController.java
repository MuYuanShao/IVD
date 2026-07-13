package com.zlth.ivd.analytics.controller;

import com.zlth.ivd.analytics.service.AnalyticsService;
import com.zlth.ivd.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard/kpi")
    public Result<Map<String, Object>> getDashboardKpi() {
        return Result.success(analyticsService.getDashboardKpi());
    }

    @GetMapping("/cost-per-test")
    public Result<Map<String, Object>> getCostPerTest(
            @RequestParam(defaultValue = "2026-07-01") String startDate,
            @RequestParam(defaultValue = "2026-07-31") String endDate) {
        return Result.success(analyticsService.getCostPerTest(startDate, endDate));
    }

    @GetMapping("/brand-comparison")
    public Result<Map<String, Object>> getBrandComparison(
            @RequestParam(defaultValue = "2026-07-01") String startDate,
            @RequestParam(defaultValue = "2026-07-31") String endDate) {
        return Result.success(analyticsService.getBrandComparison(startDate, endDate));
    }

    @GetMapping("/waste-top10")
    public Result<Map<String, Object>> getWasteTop10(
            @RequestParam(defaultValue = "2026-07-01") String startDate,
            @RequestParam(defaultValue = "2026-07-31") String endDate) {
        return Result.success(analyticsService.getWasteTop10(startDate, endDate));
    }

    @GetMapping("/audit-rate-trend")
    public Result<Map<String, Object>> getAuditRateTrend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(analyticsService.getAuditRateTrend(days));
    }
}
