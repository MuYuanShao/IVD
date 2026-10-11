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
@org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
public class ProcurementConsumptionServiceImpl implements ProcurementConsumptionService {
    private final ConsumptionRecordMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.context.event.EventListener
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onInventoryTransfer(com.zlth.ivd.common.event.InventoryTransferRecordedEvent event) {
        var record=new ConsumptionRecord();record.setId(event.recordId());record.setRecordType("TRANSFER");
        record.setBatchId(event.batchId());record.setPurchaseOrderItemId(event.purchaseOrderItemId());record.setSettlementMode(event.settlementMode());
        record.setBatchNo(event.batchNo());record.setReagentId(event.reagentId());record.setMaterialType(event.materialType());
        record.setQuantity(event.quantity());record.setUnit(event.unit());record.setUnitPrice(event.unitPrice());record.setAmount(event.amount());
        record.setWarehouseId(event.targetWarehouseId());record.setFromWarehouseId(event.sourceWarehouseId());record.setRemark(event.remark());
        record.setOperatorId(com.zlth.ivd.common.utils.SecurityUtils.getCurrentOperatorIdOrNull());
        record.setOccurredAt(java.time.LocalDateTime.now());mapper.insert(record);
    }
    public ConsumptionRecord lockRecord(Long id) {
        return mapper.selectOne(new LambdaQueryWrapper<ConsumptionRecord>().eq(ConsumptionRecord::getId,id).last("FOR UPDATE"));
    }
    public ConsumptionRecord getRecord(Long id){return mapper.selectById(id);}
    public List<ConsumptionRecord> findReceivedTransferRecords(Long reqId) {
        return mapper.selectList(new LambdaQueryWrapper<ConsumptionRecord>().eq(ConsumptionRecord::getRequisitionId,reqId)
                .eq(ConsumptionRecord::getRecordType,"TRANSFER").inSql(ConsumptionRecord::getId,"SELECT transfer_record_id FROM requisition_transfer_receipt")
                .orderByAsc(ConsumptionRecord::getId));
    }
    public Long receivedTargetBatchId(Long transferId) {
        var results=jdbc.queryForList("SELECT target_batch_id FROM requisition_transfer_receipt WHERE transfer_record_id=?",Long.class,transferId);
        return results.isEmpty()?null:results.get(0);
    }
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
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void appendRecord(ConsumptionRecord record) {
        mapper.insert(record);
        events.publishEvent(new com.zlth.ivd.common.event.StockBusinessRecordedEvent(record.getId(),record.getRecordType()));
    }
}
