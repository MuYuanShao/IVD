package com.zlth.ivd.integration.controller;

import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.integration.service.IntegrationService;
import com.zlth.ivd.common.annotation.RequireRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 外部系统集成 Controller — LIS/HIS/第三方平台接口适配
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@RestController
@RequestMapping("/api/v1/integration")
@RequireRole("SYSTEM_ADMIN")
public class IntegrationController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    /** 集成模块健康检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(integrationService.getHealth());
    }

    // ── LIS（检验信息系统）接口 ──

    /** 接收 LIS 推送的检验申请单 */
    @PostMapping("/lis/orders")
    public Result<String> receiveLisOrder(@RequestBody Map<String, Object> payload) {
        log.info("LIS 推送检验申请单: {}", payload);
        integrationService.processLisOrder(payload);
        return Result.success("LIS 申请单已接收");
    }

    /** 查询 LIS 对接状态 */
    @GetMapping("/lis/status")
    public Result<Map<String, Object>> lisStatus() {
        return Result.success(integrationService.getLisStatus());
    }

    // ── HIS（医院信息系统）接口 ──

    /** 接收 HIS 推送的患者信息 */
    @PostMapping("/his/patients")
    public Result<String> receiveHisPatient(@RequestBody Map<String, Object> payload) {
        log.info("HIS 推送患者信息: {}", payload);
        integrationService.processHisPatient(payload);
        return Result.success("HIS 患者信息已接收");
    }

    /** 查询 HIS 对接状态 */
    @GetMapping("/his/status")
    public Result<Map<String, Object>> hisStatus() {
        return Result.success(integrationService.getHisStatus());
    }

    // ── 通用 ──

    /** 集成日志查询 */
    @GetMapping("/logs")
    public Result<Map<String, Object>> listIntegrationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String system) {
        return Result.success(integrationService.listIntegrationLogs(page, pageSize, direction, system));
    }
}
