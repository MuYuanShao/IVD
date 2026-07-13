package com.zlth.ivd.audit.controller;

import com.zlth.ivd.audit.entity.AuditDecisionLog;
import com.zlth.ivd.audit.service.impl.AuditPipelineServiceImpl;
import com.zlth.ivd.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditPipelineServiceImpl pipelineService;

    /** 接口A: LIS结果推送+同步审核 */
    @PostMapping("/observations")
    public Result<AuditDecisionLog> auditObservation(@RequestBody Map<String, Object> request) {
        String observationId = (String) request.getOrDefault("observationId", "OBS-" + System.currentTimeMillis());
        String patientIdHash = (String) request.getOrDefault("patientIdHash", "hash");
        String testItemCode = (String) request.getOrDefault("testItemCode", "NA");
        String testValue = (String) request.getOrDefault("testValue", null);
        String deviceId = (String) request.getOrDefault("deviceId", null);
        String batchNo = (String) request.getOrDefault("batchNo", null);
        AuditDecisionLog result = pipelineService.executePipeline(
            observationId, patientIdHash, testItemCode, testValue, deviceId, LocalDateTime.now(), batchNo
        );
        return Result.success(result);
    }

    /** 接口B: 人工审核结果回流 */
    @PostMapping("/manual-feedback")
    public Result<String> manualFeedback(@RequestBody Map<String, Object> request) {
        String observationId = (String) request.get("observationId");
        String manualDecision = (String) request.get("manualDecision");
        Integer auditorId = (Integer) request.getOrDefault("auditorId", 1);
        String note = (String) request.getOrDefault("note", "");
        pipelineService.recordManualFeedback(observationId, manualDecision, auditorId, note);
        return Result.success("人工复核结果已记录");
    }

    /** 接口C: 批量审核状态查询 */
    @GetMapping("/status")
    public Result<List<AuditDecisionLog>> auditStatus(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.success(pipelineService.listDecisions(batchNo, start, end));
    }

    /** 决策日志列表 */
    @GetMapping("/decisions")
    public Result<List<AuditDecisionLog>> listDecisions(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.success(pipelineService.listDecisions(batchNo, start, end));
    }
}
