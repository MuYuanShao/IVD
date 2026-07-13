package com.zlth.ivd.analytics.service;

import java.util.Map;

public interface AnalyticsService {
    Map<String, Object> getDashboardKpi();
    Map<String, Object> getCostPerTest(String startDate, String endDate);
    Map<String, Object> getBrandComparison(String startDate, String endDate);
    Map<String, Object> getWasteTop10(String startDate, String endDate);
    Map<String, Object> getAuditRateTrend(int days);
}
