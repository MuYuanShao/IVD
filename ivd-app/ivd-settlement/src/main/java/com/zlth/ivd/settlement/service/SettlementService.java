package com.zlth.ivd.settlement.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import java.util.Map;

public interface SettlementService {
    PageVO<SettlementRecord> listRecords(int page, int pageSize, String settlementType, String status);
    void settleRequisition(Long requisitionId);
    void settleConsumption(Long consumptionRecordId);
    void reverseSettlement(Long settlementId, String reason);
    Map<String, Object> getStats();
}
