package com.zlth.ivd.settlement.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.vo.ConsumptionSettlementVO;
import java.util.Map;

public interface SettlementService {
    void onStockBusinessRecord(com.zlth.ivd.common.event.StockBusinessRecordedEvent event);
    PageVO<SettlementRecord> listRecords(int page, int pageSize, String settlementType, String status);
    /** 消耗结算明细：消耗数据全量字段 JOIN */
    PageVO<ConsumptionSettlementVO> getConsumptionDetails(int page, int pageSize, Long reagentId, Long deviceId, Long warehouseId);
    /** 科室签收并自动结算：状态变更 + 生成结算记录（同一事务） */
    void receiveAndSettleRequisition(Long requisitionId);
    /** 一键批量签收：签收所有待签收申领单，返回成功/失败统计 */
    Map<String, Object> batchReceiveAndSettle();
    /** 仅生成结算记录（独立结算场景） */
    void settleRequisition(Long requisitionId);
    void settleConsumption(Long consumptionRecordId);
    void settleTransfer(Long transferRecordId);
    void reverseSettlement(Long settlementId, String reason);
    java.util.List<com.zlth.ivd.settlement.vo.SettlementEventVO> history(Long settlementId);
    Map<String, Object> getStats();
}
