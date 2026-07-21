package com.zlth.ivd.finance.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.finance.entity.DailyInventorySnapshot;
import com.zlth.ivd.finance.service.FinanceService;
import com.zlth.ivd.common.annotation.RequireRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@RequireRole({"SYSTEM_ADMIN", "FINANCE"})
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/snapshots")
    public Result<PageVO<DailyInventorySnapshot>> listSnapshots(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long reagentId) {
        return Result.success(financeService.listSnapshots(page, pageSize, startDate, endDate, reagentId));
    }

    @PostMapping("/daily-settle")
    public Result<String> dailySettle(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        financeService.dailySettlement(date);
        return Result.success("日结完成");
    }

    @PostMapping("/monthly-settle")
    public Result<String> monthlySettle(@RequestParam String yearMonth) {
        financeService.monthlySettlement(yearMonth);
        return Result.success("月结完成");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(financeService.getSnapshotStats(date));
    }

    @GetMapping("/balance")
    public Result<Map<String, Object>> balance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(financeService.getBalanceSummary(startDate, endDate));
    }
}
