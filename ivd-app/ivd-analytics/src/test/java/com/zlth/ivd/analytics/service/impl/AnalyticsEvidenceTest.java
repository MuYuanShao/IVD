package com.zlth.ivd.analytics.service.impl;

import com.zlth.ivd.analytics.mapper.DashboardMapper;
import com.zlth.ivd.analytics.mapper.EfficiencyMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsEvidenceTest {
    @Test void noAuditDataProducesGapsNotRandomRates() {
        var dashboard = mock(DashboardMapper.class);
        when(dashboard.getAuditRateTrend(3)).thenReturn(List.of());
        var result = new AnalyticsServiceImpl(dashboard,mock(EfficiencyMapper.class)).getAuditRateTrend(3);
        assertEquals(3,((List<?>)result.get("dates")).size());
        assertTrue(((List<?>)result.get("rates")).stream().allMatch(java.util.Objects::isNull));
    }
    @Test void unsupportedKpisAreExplicitlyUnavailable() {
        var dashboard = mock(DashboardMapper.class);
        var result = new AnalyticsServiceImpl(dashboard,mock(EfficiencyMapper.class)).getDashboardKpi();
        assertNull(result.get("grossMargin")); assertNull(result.get("totalTestsToday"));
        assertNull(result.get("interceptAccuracy"));
    }
}
