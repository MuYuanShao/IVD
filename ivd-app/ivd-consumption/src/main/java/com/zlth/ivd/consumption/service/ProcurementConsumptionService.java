package com.zlth.ivd.consumption.service;

import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import java.util.List;

/** 采购模块可使用的消耗/调拨记录契约。 */
public interface ProcurementConsumptionService {
    void onInventoryTransfer(com.zlth.ivd.common.event.InventoryTransferRecordedEvent event);
    ConsumptionRecord lockRecord(Long id);
    ConsumptionRecord getRecord(Long id);
    List<ConsumptionRecord> findReceivedTransferRecords(Long requisitionId);
    Long receivedTargetBatchId(Long transferId);
    List<ConsumptionRecord> findTransferRecords(Long requisitionId);
    List<ConsumptionRecord> findUnreceivedTransferRecords(Long requisitionId);
    void confirmTransferReceipt(Long transferId, Long targetBatchId);
    List<ConsumptionRecord> findConsumptionRecords();
    void appendRecord(ConsumptionRecord record);
}
