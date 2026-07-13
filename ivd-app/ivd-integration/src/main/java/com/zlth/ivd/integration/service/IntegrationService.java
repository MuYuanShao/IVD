package com.zlth.ivd.integration.service;

import java.util.Map;

/**
 * 外部系统集成服务接口
 *
 * @author IVD Team
 * @since 2026-07-09
 */
public interface IntegrationService {

    /** 集成模块健康状态 */
    Map<String, Object> getHealth();

    /** 处理 LIS 推送的检验申请单 */
    void processLisOrder(Map<String, Object> payload);

    /** LIS 对接状态 */
    Map<String, Object> getLisStatus();

    /** 处理 HIS 推送的患者信息 */
    void processHisPatient(Map<String, Object> payload);

    /** HIS 对接状态 */
    Map<String, Object> getHisStatus();

    /** 集成日志查询 */
    Map<String, Object> listIntegrationLogs(int page, int pageSize, String direction, String system);
}
