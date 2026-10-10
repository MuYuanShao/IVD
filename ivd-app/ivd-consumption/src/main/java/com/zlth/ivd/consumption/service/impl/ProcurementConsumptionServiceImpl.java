package com.zlth.ivd.consumption.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.ConsumptionRecordTypeEnum;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.consumption.service.ProcurementConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcurementConsumptionServiceImpl implements ProcurementConsumptionService {
    private final ConsumptionRecordMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public List<ConsumptionRecord> findUnreceivedTransferRecords(Long requisitionId) {
        return mapper.selectList(new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRequisitionId, requisitionId)
                .eq(ConsumptionRecord::getRecordType, ConsumptionRecordTypeEnum.TRANSFER.getCode())
                .notInSql(ConsumptionRecord::getId, "SELECT transfer_record_id FROM requisition_transfer_receipt"));
    }
    public void confirmTransferReceipt(Long transferId, Long targetBatchId) {
        jdbc.update("INSERT INTO requisition_transfer_receipt(transfer_record_id,target_batch_id,received_by,received_at) VALUES(?,?,?,CURRENT_TIMESTAMP(3))",
                transferId, targetBatchId, com.zlth.ivd.common.utils.SecurityUtils.getCurrentUserIdOrNull());
    }
    public List<ConsumptionRecord> findTransferRecords(Long requisitionId) {
        return mapper.selectList(new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRequisitionId, requisitionId)
                .eq(ConsumptionRecord::getRecordType, ConsumptionRecordTypeEnum.TRANSFER.getCode()));
    }
    public List<ConsumptionRecord> findConsumptionRecords() {
        return mapper.selectList(new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRecordType, ConsumptionRecordTypeEnum.CONSUME.getCode()));
    }
    public void appendRecord(ConsumptionRecord record) { mapper.insert(record); }
}
