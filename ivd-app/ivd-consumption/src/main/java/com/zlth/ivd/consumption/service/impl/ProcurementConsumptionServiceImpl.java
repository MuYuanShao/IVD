package com.zlth.ivd.consumption.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.ConsumptionRecordTypeEnum;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.consumption.service.ProcurementConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
public class ProcurementConsumptionServiceImpl implements ProcurementConsumptionService {
    private final ConsumptionRecordMapper mapper;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final DataSource dataSource;
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
    @Override
    public boolean hasVerifiedInboundSource(ConsumptionRecord source) {
        if (source.getId() == null || source.getBatchId() == null || source.getReagentId() == null
                || source.getBatchNo() == null || source.getBatchNo().isBlank() || source.getOccurredAt() == null
                || source.getQuantity() == null || source.getQuantity().signum() <= 0 || source.getUnitPrice() == null)
            return false;
        Long warehouse = "TRANSFER".equals(source.getRecordType())
                ? source.getFromWarehouseId() : source.getWarehouseId();
        if (warehouse == null) return false;
        long institution = InstitutionContext.require().institutionId();
        // This internal existence check must see central-warehouse evidence when a department signs
        // or consumes. Every query is explicitly limited to this institution; no evidence rows leave here.
        var evidence = new JdbcTemplate(dataSource);
        if (source.getPurchaseOrderItemId() != null) {
            boolean inbound = "INBOUND".equals(source.getRecordType());
            String exactReceipt = inbound ? " AND r.id=? AND r.batch_id=?" : "";
            var args = new ArrayList<Object>(List.of(source.getBatchId(), warehouse, institution, source.getPurchaseOrderItemId(),
                    source.getReagentId(), source.getBatchNo().trim(), source.getUnitPrice(),
                    source.getQuantity(), source.getOccurredAt()));
            if (inbound) {
                args.add(source.getId());
                args.add(source.getBatchId());
            }
            return !evidence.queryForList("""
                    SELECT r.id FROM consumption_record r
                    JOIN purchase_order_item i ON i.institution_id=r.institution_id
                      AND i.id=r.purchase_order_item_id AND i.reagent_id=r.reagent_id
                    JOIN reagent_batch b ON b.institution_id=r.institution_id AND b.id=? AND b.warehouse_id=?
                      AND b.origin_purchase_order_item_id=i.id AND b.reagent_id=r.reagent_id AND b.batch_no=r.batch_no
                    WHERE r.institution_id=? AND r.purchase_order_item_id=? AND r.reagent_id=?
                      AND r.batch_no=? AND r.record_type='INBOUND' AND r.batch_id IS NOT NULL
                      AND r.quantity>0 AND r.unit_price=? AND i.received_quantity>=? AND r.occurred_at<=?
                    """ + exactReceipt + " LIMIT 1", Long.class, args.toArray()).isEmpty();
        }
        if ("INBOUND".equals(source.getRecordType())) return false;
        var cutoffs = evidence.queryForList("""
                SELECT o.effective_at FROM inventory_opening_balance o
                JOIN reagent_batch b ON b.institution_id=o.institution_id AND b.id=o.batch_id
                  AND b.reagent_id=o.reagent_id AND b.batch_no=o.batch_no AND b.warehouse_id=o.warehouse_id
                WHERE o.institution_id=? AND o.batch_id=? AND o.reagent_id=? AND o.batch_no=? AND o.warehouse_id=?
                  AND b.origin_purchase_order_item_id IS NULL AND o.quantity>=b.stock+?
                  AND o.unit_price=? AND o.effective_at<=? AND o.created_at<=?
                  AND TRIM(o.evidence_source)<>'' AND TRIM(o.evidence_reference)<>''
                """, Timestamp.class, institution, source.getBatchId(), source.getReagentId(),
                source.getBatchNo(), warehouse, source.getQuantity(), source.getUnitPrice(),
                source.getOccurredAt(), source.getOccurredAt());
        if (cutoffs.size() != 1) return false;
        // Later inflows may mix provenance. Never infer the qualifying portion by lot number.
        return evidence.queryForList("""
                SELECT id FROM consumption_record
                WHERE institution_id=? AND reagent_id=? AND batch_no=? AND warehouse_id=?
                  AND record_type IN ('INBOUND','TRANSFER') AND (occurred_at IS NULL OR occurred_at>=?)
                LIMIT 1 FOR UPDATE
                """, Long.class, institution, source.getReagentId(), source.getBatchNo(), warehouse, cutoffs.get(0)).isEmpty();
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void appendRecord(ConsumptionRecord record) {
        mapper.insert(record);
        events.publishEvent(new com.zlth.ivd.common.event.StockBusinessRecordedEvent(record.getId(),record.getRecordType()));
    }
}
