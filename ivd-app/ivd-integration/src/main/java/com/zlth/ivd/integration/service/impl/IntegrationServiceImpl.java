package com.zlth.ivd.integration.service.impl;

import com.zlth.ivd.integration.service.IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部系统集成服务实现（一期骨架，后续接入 LIS/HIS SDK）
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Service
public class IntegrationServiceImpl implements IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationServiceImpl.class);

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("module", "ivd-integration");
        health.put("status", "UP");
        health.put("lis", getLisStatus());
        health.put("his", getHisStatus());
        return health;
    }

    @Override
    public void processLisOrder(Map<String, Object> payload) {
        // TODO: 一期仅记录日志，后续解析 HL7/ASTM 消息并创建院内检验申请
        log.info("[LIS] 接收检验申请单 — 待解析 HL7/ASTM 消息");
        if (payload != null) {
            log.info("[LIS] payload keys: {}", payload.keySet());
        }
    }

    @Override
    public Map<String, Object> getLisStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", false);
        status.put("protocol", "HL7 v2.x / ASTM");
        status.put("endpoint", "待配置");
        status.put("lastSyncAt", null);
        return status;
    }

    @Override
    public void processHisPatient(Map<String, Object> payload) {
        // TODO: 一期仅记录日志，后续解析患者信息并关联院内患者
        log.info("[HIS] 接收患者信息 — 待解析");
        if (payload != null) {
            log.info("[HIS] payload keys: {}", payload.keySet());
        }
    }

    @Override
    public Map<String, Object> getHisStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", false);
        status.put("protocol", "REST / HL7");
        status.put("endpoint", "待配置");
        status.put("lastSyncAt", null);
        return status;
    }

    @Override
    public Map<String, Object> listIntegrationLogs(int page, int pageSize, String direction, String system) {
        // TODO: 一期返回空，后续接入集成日志表
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("records", java.util.Collections.emptyList());
        return result;
    }
}
