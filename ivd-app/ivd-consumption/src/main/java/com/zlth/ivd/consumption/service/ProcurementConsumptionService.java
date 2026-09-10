package com.zlth.ivd.consumption.service;

import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import java.util.List;

/** 采购模块可使用的消耗/调拨记录契约。 */
public interface ProcurementConsumptionService {
    List<ConsumptionRecord> findTransferRecords(Long requisitionId);
    List<ConsumptionRecord> findConsumptionRecords();
    void appendRecord(ConsumptionRecord record);
}
