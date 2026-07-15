package com.zlth.ivd.consumption.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.vo.ConsumptionRecordVO;
import java.math.BigDecimal;
import java.util.Map;

public interface ConsumptionService {
    PageVO<ConsumptionRecordVO> listRecords(int page, int pageSize, String recordType, Long deviceId, Long reagentId);
    void recordConsumption(Long batchId, Long deviceId, BigDecimal qty, String purpose, String remark);
    void recordInbound(Long batchId, BigDecimal qty, Long warehouseId);
    void batchConsume(String batchNo, Long deviceId, BigDecimal qty);
    Map<String, Object> getStats(Long deviceId);
}
