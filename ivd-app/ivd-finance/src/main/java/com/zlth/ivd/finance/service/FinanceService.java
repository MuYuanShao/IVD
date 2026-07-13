package com.zlth.ivd.finance.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.finance.entity.DailyInventorySnapshot;
import java.time.LocalDate;
import java.util.Map;

public interface FinanceService {
    PageVO<DailyInventorySnapshot> listSnapshots(int page, int pageSize, LocalDate startDate, LocalDate endDate, Long reagentId);
    void dailySettlement(LocalDate date);
    void monthlySettlement(String yearMonth);
    Map<String, Object> getSnapshotStats(LocalDate date);
    Map<String, Object> getBalanceSummary(LocalDate startDate, LocalDate endDate);
}
