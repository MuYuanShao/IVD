package com.zlth.ivd.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.SettlementStatusEnum;
import com.zlth.ivd.common.enums.SettlementTypeEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.exception.DuplicateSettlementException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.common.enums.ConsumptionRecordTypeEnum;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.entity.PurchaseRequisitionItem;
import com.zlth.ivd.procurement.service.ProcurementService;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.mapper.SettlementRecordMapper;
import com.zlth.ivd.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zlth.ivd.common.utils.IdGenerator;
import com.zlth.ivd.settlement.vo.ConsumptionSettlementVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRecordMapper recordMapper;
    private final com.zlth.ivd.consumption.service.ProcurementConsumptionService consumption;
    private final ProcurementService procurementService;
    private final JdbcTemplate jdbcTemplate;
    private final com.zlth.ivd.settlement.mapper.SettlementEventMapper eventMapper;

    @Lazy
    @Autowired
    private SettlementService self;

    @Override
    public PageVO<SettlementRecord> listRecords(int page, int pageSize, String settlementType, String status) {
        LambdaQueryWrapper<SettlementRecord> qw = new LambdaQueryWrapper<>();
        if (settlementType != null) qw.eq(SettlementRecord::getSettlementType, settlementType);
        if (status != null) qw.eq(SettlementRecord::getStatus, status);
        qw.orderByDesc(SettlementRecord::getSettledAt);
        Page<SettlementRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
        return new PageVO<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,rollbackFor = Exception.class)
    public void receiveAndSettleRequisition(Long requisitionId) {
        // 1. 状态变更：SHIPPED/PARTIAL_FULFILLED → FULFILLED
        procurementService.receiveRequisition(requisitionId);
        // 2. 自动生成结算记录
        settleReceivedMovements(requisitionId,false);
        log.info("科室签收+结算完成: requisitionId={}", requisitionId);
    }

    @Override
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Map<String, Object> batchReceiveAndSettle() {
        List<PurchaseRequisition> pending = procurementService.pendingReceipts();

        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (PurchaseRequisition req : pending) {
            try {
                self.receiveAndSettleRequisition(req.getId());
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(req.getReqNo() + ": " + e.getMessage());
                log.error("批量签收失败: reqNo={}, error={}", req.getReqNo(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pending.size());
        result.put("success", success);
        result.put("failed", failed);
        if (!errors.isEmpty()) result.put("errors", errors);
        log.info("批量签收完成: total={}, success={}, failed={}", pending.size(), success, failed);
        return result;
    }

    @Override
    public void settleRequisition(Long requisitionId) {
        settleReceivedMovements(requisitionId,true);
    }

    private void settleReceivedMovements(Long requisitionId,boolean manualRequisitionOnly) {
        PurchaseRequisition req = procurementService.lockRequisitionForSettlement(requisitionId);
        if (req.getStatus() != RequisitionStatusEnum.FULFILLED && req.getStatus() != RequisitionStatusEnum.PARTIAL_FULFILLED)
            throw new BizException(40001, "请先完成科室签收后结算");
        var movements = procurementService.receivedTransfers(requisitionId);
        if (movements.isEmpty()) {
            if(manualRequisitionOnly)throw new BizException(40001,"未找到带明细关联的实际发货流水，历史单据请先核对来源");
            return;
        }
        int added = 0;
        for (var move : movements) {
            var mode=com.zlth.ivd.common.enums.SettlementModeEnum.resolve(move.getSettlementMode());
            if(mode!=com.zlth.ivd.common.enums.SettlementModeEnum.REQUISITION
                    && (manualRequisitionOnly || mode!=com.zlth.ivd.common.enums.SettlementModeEnum.TRANSFER)) continue;
            String type=mode.name();Long sourceId=mode==com.zlth.ivd.common.enums.SettlementModeEnum.REQUISITION?requisitionId:move.getId();
            if(recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getSettlementType,"REQUISITION")
                    .eq(SettlementRecord::getSourceId,requisitionId).eq(SettlementRecord::getSourceItemKind,"LEGACY"))>0)
                throw new DuplicateSettlementException(req.getReqNo());
            if(settlementExists(type,sourceId,move.getId())) continue;
            insertFromEvidence(move,type,sourceId,"TRANSFER_RECORD",req.getReqNo(),req.getFromDeptId());added++;
        }
        if (manualRequisitionOnly && added==0) throw new BizException(40900,"该单没有待结算的领用模式明细，或已结算；其他模式按对应业务触发");
    }

    @Override
    public void settleTransfer(Long transferRecordId) {
        ConsumptionRecord source = consumption.getRecord(transferRecordId);
        if (source == null) throw new BizException(40400, "调拨记录不存在");
        if(!"TRANSFER".equals(source.getRecordType()) || source.getRequisitionId()==null)
            throw new BizException(40001,"仅带申领签收证据的调拨流水可结算");
        var req=procurementService.lockRequisitionForSettlement(source.getRequisitionId());
        source=consumption.lockRecord(transferRecordId);
        requireMode(source,com.zlth.ivd.common.enums.SettlementModeEnum.TRANSFER);
        if(procurementService.receivedTransfers(req.getId()).stream().noneMatch(row->row.getId().equals(transferRecordId)))
            throw new BizException(40001,"请先完成科室签收");
        if (settlementExists(SettlementTypeEnum.TRANSFER.getCode(), transferRecordId, transferRecordId)) {
            throw new BizException(40900, "该调拨记录已结算");
        }

        insertFromEvidence(source,"TRANSFER",transferRecordId,"TRANSFER_RECORD",req.getReqNo(),req.getFromDeptId());
    }

    @Override
    public void settleConsumption(Long consumptionRecordId) {
        // 从消耗记录读取真实数据
        ConsumptionRecord source = consumption.lockRecord(consumptionRecordId);
        if (source == null) {
            throw new BizException(40400, "消耗记录不存在");
        }
        if (!ConsumptionRecordTypeEnum.CONSUME.getCode().equals(source.getRecordType())) {
            throw new BizException(40001, "该记录不是消耗类型");
        }
        requireMode(source,com.zlth.ivd.common.enums.SettlementModeEnum.CONSUMPTION);
        if (settlementExists(SettlementTypeEnum.CONSUMPTION.getCode(), consumptionRecordId, consumptionRecordId)) {
            throw new BizException(40900, "该消耗记录已结算");
        }

        insertFromEvidence(source,"CONSUMPTION",source.getId(),"CONSUMPTION_RECORD","CNS-"+source.getId(),consumptionDepartment(source));
    }

    @Override
    public void reverseSettlement(Long settlementId, String reason) {
        if(reason==null || reason.isBlank() || reason.trim().length()>500) throw new BizException(40001,"冲销原因必填且不得超过500字");
        Long operatorId = SecurityUtils.getCurrentOperatorIdOrNull();
        SettlementRecord record = recordMapper.lockById(settlementId);
        if (record == null) throw new BizException(40400, "结算记录不存在");
        if (SettlementStatusEnum.REVERSED.getCode().equals(record.getStatus())) throw new BizException(40001, "该记录已被冲销，不能重复冲销");
        if(!"ACTIVE".equals(record.getStatus()))throw new BizException(40900,"仅有效结算可以冲销");

        record.setStatus(SettlementStatusEnum.REVERSED.getCode());
        record.setReversedAt(LocalDateTime.now());
        if(recordMapper.updateById(record)!=1) throw new BizException(40900,"结算状态已变化");
        appendEvent(record,"ACTIVE","REVERSED",reason.trim());
        log.info("冲销结算: id={}, reason={}, operatorId={}", settlementId, reason, operatorId);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", recordMapper.selectCount(null));
        stats.put("active", recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getStatus, "ACTIVE")));
        stats.put("reversed", recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getStatus, SettlementStatusEnum.REVERSED.getCode())));
        stats.put("monthlyAmount",jdbcTemplate.queryForObject("SELECT CASE WHEN COUNT(*)<>COUNT(amount) THEN NULL ELSE COALESCE(SUM(amount),0) END FROM settlement_record WHERE status='ACTIVE' AND settled_at>=?",
                BigDecimal.class,java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay()));
        return stats;
    }

    @Override @Transactional(readOnly=true)
    public List<com.zlth.ivd.settlement.vo.SettlementEventVO> history(Long id) {
        if(recordMapper.selectById(id)==null)throw new BizException(40400,"结算不存在或不可访问");
        return eventMapper.selectList(new LambdaQueryWrapper<com.zlth.ivd.settlement.entity.SettlementEvent>()
                .eq(com.zlth.ivd.settlement.entity.SettlementEvent::getSettlementId,id)
                .orderByAsc(com.zlth.ivd.settlement.entity.SettlementEvent::getId)).stream()
                .map(row->new com.zlth.ivd.settlement.vo.SettlementEventVO(row.getId(),row.getFromStatus(),row.getToStatus(),
                        row.getReason(),row.getOperatorId(),row.getOperatorName(),row.getCreatedAt())).toList();
    }

    /**
     * 生成全局唯一结算单号（Snowflake 算法，保证分布式唯一）
     */
    private String generateSettlementNo(String prefix) {
        return "STL-" + prefix + "-" + IdGenerator.nextIdStr();
    }

    private boolean settlementExists(String type, Long sourceId, Long sourceItemId) {
        return recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>()
                .eq(SettlementRecord::getSettlementType, type)
                .eq(SettlementRecord::getSourceId, sourceId)
                .eq(SettlementRecord::getSourceItemId, sourceItemId)) > 0;
    }

    private void insertSettlement(SettlementRecord record, String sourceNo) {
        try {
            record.setId(IdGenerator.nextId());
            recordMapper.insert(record);
            appendEvent(record,null,"ACTIVE","业务来源="+sourceNo+"，类型="+record.getSettlementType());
        } catch (DuplicateKeyException e) {
            throw new BizException(40900, "来源单据已结算: " + sourceNo);
        }
    }

    @org.springframework.context.event.EventListener
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public void onStockBusinessRecord(com.zlth.ivd.common.event.StockBusinessRecordedEvent event) {
        if(!"INBOUND".equals(event.recordType()) && !"CONSUME".equals(event.recordType()))return;
        var source=consumption.lockRecord(event.recordId());
        if(source==null || !event.recordType().equals(source.getRecordType()))throw new BizException(40900,"结算事件来源不一致");
        var mode=com.zlth.ivd.common.enums.SettlementModeEnum.resolve(source.getSettlementMode());
        var expected="INBOUND".equals(event.recordType())?com.zlth.ivd.common.enums.SettlementModeEnum.INBOUND:
                com.zlth.ivd.common.enums.SettlementModeEnum.CONSUMPTION;
        if(mode!=expected)return;
        if(settlementExists(mode.name(),source.getId(),source.getId()))return;
        insertFromEvidence(source,mode.name(),source.getId(),"INBOUND".equals(event.recordType())?"INBOUND_RECORD":"CONSUMPTION_RECORD",
                event.recordType()+"-"+source.getId(), "CONSUME".equals(event.recordType()) ? consumptionDepartment(source) : null);
    }

    private void requireMode(ConsumptionRecord source,com.zlth.ivd.common.enums.SettlementModeEnum expected) {
        if(com.zlth.ivd.common.enums.SettlementModeEnum.resolve(source.getSettlementMode())!=expected)
            throw new BizException(40001,"该来源不是所选结算模式；未知历史来源须先人工核对，不能重复计费");
    }
    private void insertFromEvidence(ConsumptionRecord source,String type,Long sourceId,String kind,String sourceNo,Long departmentId) {
        if(source.getQuantity()==null || source.getQuantity().signum()<=0 || source.getUnitPrice()==null
                || source.getUnitPrice().signum()<0 || source.getAmount()==null || source.getBatchId()==null
                || source.getBatchNo()==null || source.getReagentId()==null)
            throw new BizException(40001,"来源数量、批次或金额证据不完整，结算已拒绝");
        if(source.getQuantity().multiply(source.getUnitPrice()).setScale(2,java.math.RoundingMode.HALF_UP).compareTo(source.getAmount())!=0)
            throw new BizException(40900,"来源金额与数量单价不一致");
        String prefix=switch(type){case "CONSUMPTION"->"CNS";case "TRANSFER"->"TRF";case "REQUISITION"->"REQ";case "INBOUND"->"INB";default->throw new BizException(40001,"不支持的结算类型");};
        var record=new SettlementRecord();record.setSettlementNo(generateSettlementNo(prefix));
        record.setSettlementType(type);record.setSourceId(sourceId);record.setSourceItemKind(kind);record.setSourceItemId(source.getId());
        record.setSourceNo(sourceNo);record.setBatchNo(source.getBatchNo());record.setReagentId(source.getReagentId());
        record.setQuantity(source.getQuantity());record.setUnitPrice(source.getUnitPrice());record.setAmount(source.getAmount());
        record.setStatus("ACTIVE");record.setFromDeptId(departmentId);record.setSettledBy(SecurityUtils.getCurrentOperatorIdOrNull());
        record.setSettledAt(LocalDateTime.now());insertSettlement(record,sourceNo);
    }
    private Long consumptionDepartment(ConsumptionRecord source) {
        if (source.getWarehouseId() == null) throw new BizException(40001, "消耗流水缺少库房归属证据");
        var departments = jdbcTemplate.query("""
                SELECT COALESCE(w.dept_id,p.dept_id,g.dept_id) department_id
                FROM warehouse w
                LEFT JOIN warehouse p ON p.id=w.parent_id AND p.institution_id=w.institution_id
                LEFT JOIN warehouse g ON g.id=p.parent_id AND g.institution_id=w.institution_id
                WHERE w.id=? AND w.level IN (2,3)
                """, (rs, row) -> rs.getObject("department_id", Long.class), source.getWarehouseId());
        if (departments.isEmpty() || departments.get(0) == null)
            throw new BizException(40001, "无法核对消耗库房所属科室，已拒绝结算");
        return departments.get(0);
    }
    private void appendEvent(SettlementRecord record,String from,String to,String reason) {
        var event=new com.zlth.ivd.settlement.entity.SettlementEvent();event.setSettlementId(record.getId());
        event.setFromStatus(from);event.setToStatus(to);event.setReason(reason);
        event.setOperatorId(SecurityUtils.getCurrentOperatorIdOrNull());event.setOperatorName(SecurityUtils.getCurrentUsername());
        event.setCreatedAt(LocalDateTime.now());eventMapper.insert(event);
    }

    // ── 消耗结算明细（消耗数据全量字段 JOIN）──

    private static final String CONSUMPTION_DETAIL_SQL = """
            SELECT
                sr.settlement_no,
                sr.id AS settlement_id,
                sr.amount AS settlement_amount,
                cr.id AS consumption_record_id,
                cr.reagent_id AS source_reagent_id,
                cr.batch_no,
                COALESCE(cr.unit,rc.unit) AS unit,
                COALESCE(sr.status,'UNSETTLED') AS settlement_status,
                d.dept_name,
                COALESCE(w.name, rb_wh.name) AS warehouse_name,
                rc.generic_name AS reagent_name,
                rc.generic_name AS generic_name,
                rc.specification,
                rc.manufacturer,
                rc.distributor,
                cr.unit_price,
                rb.unit_price AS batch_unit_price,
                cr.quantity,
                cr.amount,
                u.real_name AS consumer_name,
                CASE WHEN rc.is_volume_based = 1 THEN '是' ELSE '否' END AS is_volume_based,
                rc.registration_cert_no,
                CASE WHEN rc.is_insurance_payment = 1 THEN '是' ELSE '否' END AS is_insurance_payment,
                rc.volume_type,
                rc.insurance_payment_type,
                CASE WHEN rc.is_chargeable = 1 THEN '是' ELSE '否' END AS is_chargeable,
                dev.device_code,
                dev.name AS device_name,
                cr.occurred_at
            FROM consumption_record cr
            LEFT JOIN reagent_catalog rc ON cr.reagent_id = rc.id AND rc.is_deleted = 0
            LEFT JOIN device dev ON cr.device_id = dev.id
            LEFT JOIN settlement_record sr ON sr.id=(SELECT s.id FROM settlement_record s WHERE s.settlement_type='CONSUMPTION' AND s.source_id=cr.id ORDER BY s.settled_at DESC,s.id DESC LIMIT 1)
            LEFT JOIN reagent_batch rb ON rb.id=COALESCE(cr.batch_id,(SELECT MIN(b.id) FROM reagent_batch b WHERE b.batch_no=cr.batch_no AND b.reagent_id=cr.reagent_id AND b.warehouse_id=cr.warehouse_id))
            LEFT JOIN warehouse w ON cr.warehouse_id = w.id
            LEFT JOIN warehouse rb_wh ON rb.warehouse_id = rb_wh.id
            LEFT JOIN sys_user u ON cr.operator_id = u.id
            LEFT JOIN sys_dept d ON u.dept_id = d.id
            WHERE cr.record_type = 'CONSUME'
            """;

    @Override
    public PageVO<ConsumptionSettlementVO> getConsumptionDetails(int page, int pageSize,
            Long reagentId, Long deviceId, Long warehouseId, java.time.LocalDate date, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > 500) throw new BizException(40001, "分页参数无效");
        StringBuilder sql = new StringBuilder(CONSUMPTION_DETAIL_SQL);
        List<Object> params = new ArrayList<>();

        if (reagentId != null) {
            sql.append(" AND cr.reagent_id = ?");
            params.add(reagentId);
        }
        if (deviceId != null) {
            sql.append(" AND cr.device_id = ?");
            params.add(deviceId);
        }
        if (warehouseId != null) {
            sql.append(" AND cr.warehouse_id = ?");
            params.add(warehouseId);
        }
        if (date != null) {
            sql.append(" AND cr.occurred_at >= ? AND cr.occurred_at < ?");
            params.add(date.atStartOfDay());
            params.add(date.plusDays(1).atStartOfDay());
        }
        if (keyword != null && !keyword.isBlank()) {
            String search = keyword.strip();
            if (search.length() > 100) throw new BizException(40001, "试剂关键词不能超过100字符");
            sql.append(" AND (LOCATE(?, rc.generic_name) > 0 OR LOCATE(?, rc.his_code) > 0)");
            params.add(search);
            params.add(search);
        }

        String summarySql = """
                SELECT COUNT(*) record_count, COUNT(DISTINCT source_reagent_id) reagent_count,
                       COUNT(CASE WHEN settlement_status='ACTIVE' THEN 1 END) active_count,
                       CASE WHEN COUNT(CASE WHEN settlement_status='ACTIVE' AND settlement_amount IS NULL THEN 1 END)>0
                            THEN NULL ELSE COALESCE(SUM(CASE WHEN settlement_status='ACTIVE' THEN settlement_amount END),0)
                       END active_amount
                FROM (
                """ + sql + ") filtered_details";
        var totals = jdbcTemplate.queryForMap(summarySql, params.toArray());

        // Paginated query
        sql.append(" ORDER BY cr.occurred_at DESC, cr.id DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((long) (page - 1) * pageSize);

        List<ConsumptionSettlementVO> records = new ArrayList<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            ConsumptionSettlementVO vo = new ConsumptionSettlementVO();
            vo.setSettlementNo(rs.getString("settlement_no"));
            vo.setSettlementId(rs.getObject("settlement_id",Long.class));
            vo.setConsumptionRecordId(rs.getLong("consumption_record_id"));
            vo.setBatchNo(rs.getString("batch_no"));
            vo.setUnit(rs.getString("unit"));
            vo.setSettlementStatus(rs.getString("settlement_status"));
            vo.setDeptName(rs.getString("dept_name"));
            vo.setWarehouseName(rs.getString("warehouse_name"));
            vo.setReagentName(rs.getString("reagent_name"));
            vo.setGenericName(rs.getString("generic_name"));
            vo.setSpecification(rs.getString("specification"));
            vo.setManufacturer(rs.getString("manufacturer"));
            vo.setDistributor(rs.getString("distributor"));
            BigDecimal unitPrice = rs.getBigDecimal("unit_price");
            vo.setUnitPrice(unitPrice);
            BigDecimal batchUnitPrice = rs.getBigDecimal("batch_unit_price");
            vo.setBatchUnitPrice(batchUnitPrice);
            BigDecimal quantity = rs.getBigDecimal("quantity");
            vo.setQuantity(quantity);
            BigDecimal amount = rs.getBigDecimal("amount");
            vo.setAmount(amount);
            vo.setConsumerName(rs.getString("consumer_name"));
            vo.setIsVolumeBased(rs.getString("is_volume_based"));
            vo.setRegistrationCertNo(rs.getString("registration_cert_no"));
            vo.setIsInsurancePayment(rs.getString("is_insurance_payment"));
            vo.setVolumeType(rs.getString("volume_type"));
            vo.setInsurancePaymentType(rs.getString("insurance_payment_type"));
            vo.setIsChargeable(rs.getString("is_chargeable"));
            vo.setDeviceCode(rs.getString("device_code"));
            vo.setDeviceName(rs.getString("device_name"));
            vo.setOccurredAt(rs.getTimestamp("occurred_at") != null
                    ? rs.getTimestamp("occurred_at").toLocalDateTime() : null);
            records.add(vo);
        }, params.toArray());

        var summary = new com.zlth.ivd.settlement.vo.ConsumptionDetailsPageVO.Summary(
                ((Number) totals.get("active_count")).longValue(),
                (BigDecimal) totals.get("active_amount"),
                ((Number) totals.get("reagent_count")).longValue());
        return new com.zlth.ivd.settlement.vo.ConsumptionDetailsPageVO(
                ((Number) totals.get("record_count")).longValue(), page, pageSize, records, summary);
    }
}
