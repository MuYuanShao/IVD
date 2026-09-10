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
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.procurement.entity.PurchaseRequisition;
import com.zlth.ivd.procurement.entity.PurchaseRequisitionItem;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionMapper;
import com.zlth.ivd.procurement.mapper.PurchaseRequisitionItemMapper;
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
    private final ConsumptionRecordMapper consumptionRecordMapper;
    private final PurchaseRequisitionMapper reqMapper;
    private final PurchaseRequisitionItemMapper reqItemMapper;
    private final ProcurementService procurementService;
    private final JdbcTemplate jdbcTemplate;

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
    @Transactional(rollbackFor = Exception.class)
    public void receiveAndSettleRequisition(Long requisitionId) {
        // 1. 状态变更：SHIPPED/PARTIAL_FULFILLED → FULFILLED
        procurementService.receiveRequisition(requisitionId);
        // 2. 自动生成结算记录
        settleRequisition(requisitionId);
        log.info("科室签收+结算完成: requisitionId={}", requisitionId);
    }

    @Override
    public Map<String, Object> batchReceiveAndSettle() {
        List<PurchaseRequisition> pending = reqMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisition>()
                        .in(PurchaseRequisition::getStatus,
                                RequisitionStatusEnum.SHIPPED, RequisitionStatusEnum.PARTIAL_FULFILLED));

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
        if (jdbcTemplate.queryForList("SELECT id FROM purchase_requisition WHERE id=? FOR UPDATE", requisitionId).isEmpty())
            throw new BizException(40400, "申领单不存在");
        PurchaseRequisition req = reqMapper.selectById(requisitionId);
        if (req.getStatus() != RequisitionStatusEnum.FULFILLED)
            throw new BizException(40001, "请先完成科室签收后结算");
        Long legacy = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_record WHERE settlement_type='REQUISITION' AND source_id=? AND source_item_kind='LEGACY'", Long.class, requisitionId);
        if (legacy != null && legacy > 0) throw new DuplicateSettlementException(req.getReqNo());
        List<Map<String,Object>> movements = jdbcTemplate.queryForList(
                "SELECT cr.* FROM consumption_record cr JOIN purchase_requisition_item i ON i.id=cr.requisition_item_id AND i.req_id=cr.requisition_id AND i.reagent_id=cr.reagent_id WHERE cr.requisition_id=? AND cr.record_type='TRANSFER' ORDER BY cr.id", requisitionId);
        if (movements.isEmpty()) throw new BizException(40001,"未找到带明细关联的实际发货流水，历史单据请先核对来源");
        int added = 0;
        for (Map<String,Object> move : movements) {
            Long movementId = ((Number)move.get("id")).longValue();
            Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement_record WHERE settlement_type='REQUISITION' AND source_id=? AND source_item_kind='TRANSFER_RECORD' AND source_item_id=?",Long.class,requisitionId,movementId);
            if (existing != null && existing > 0) continue;
            BigDecimal qty = (BigDecimal)move.get("quantity"), price = (BigDecimal)move.get("unit_price"), amount = (BigDecimal)move.get("amount");
            if (qty == null || qty.signum() <= 0 || price == null || amount == null || move.get("batch_no")==null)
                throw new BizException(40001,"发货流水数量、金额或批号不完整，不能生成结算");
            SettlementRecord record = new SettlementRecord();
            record.setSettlementNo(generateSettlementNo("REQ"));
            record.setSettlementType("REQUISITION");
            record.setSourceId(requisitionId);
            record.setSourceItemKind("TRANSFER_RECORD");
            record.setSourceItemId(movementId);
            record.setSourceNo(req.getReqNo());
            record.setBatchNo(move.get("batch_no").toString());
            record.setReagentId(((Number)move.get("reagent_id")).longValue());
            record.setQuantity(qty); record.setUnitPrice(price); record.setAmount(amount);
            record.setStatus("ACTIVE");
            record.setFromDeptId(req.getFromDeptId() == null ? null : req.getFromDeptId().intValue());
            record.setSettledBy(SecurityUtils.getCurrentUserId().intValue());
            record.setSettledAt(LocalDateTime.now());
            insertSettlement(record, req.getReqNo()); added++;
        }
        if (added == 0) throw new DuplicateSettlementException(req.getReqNo());
    }

    @Override
    public void settleTransfer(Long transferRecordId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        ConsumptionRecord source = consumptionRecordMapper.selectById(transferRecordId);
        if (source == null) throw new BizException(40400, "调拨记录不存在");
        if (!ConsumptionRecordTypeEnum.TRANSFER.getCode().equals(source.getRecordType())) {
            throw new BizException(40000, "该记录不是调拨类型");
        }
        if (settlementExists(SettlementTypeEnum.TRANSFER.getCode(), transferRecordId, transferRecordId)) {
            throw new BizException(40900, "该调拨记录已结算");
        }

        String no = generateSettlementNo("TRF");
        SettlementRecord record = new SettlementRecord();
        record.setSettlementNo(no);
        record.setSettlementType(SettlementTypeEnum.TRANSFER.getCode());
        record.setSourceId(transferRecordId);
        record.setSourceItemId(transferRecordId);
        record.setSourceNo("TRF-" + source.getId());
        record.setBatchNo(source.getBatchNo());
        record.setReagentId(source.getReagentId());
        record.setQuantity(source.getQuantity());
        record.setUnitPrice(source.getUnitPrice());
        record.setAmount(source.getAmount());
        record.setStatus("ACTIVE");
        record.setSettledBy(operatorId);
        record.setSettledAt(LocalDateTime.now());
        insertSettlement(record, record.getSourceNo());
        log.info("调拨结算: no={}, transferRecordId={}", no, transferRecordId);
    }

    @Override
    public void settleConsumption(Long consumptionRecordId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 从消耗记录读取真实数据
        ConsumptionRecord source = consumptionRecordMapper.selectById(consumptionRecordId);
        if (source == null) {
            throw new BizException(40400, "消耗记录不存在");
        }
        if (!ConsumptionRecordTypeEnum.CONSUME.getCode().equals(source.getRecordType())) {
            throw new BizException(40001, "该记录不是消耗类型");
        }
        if (settlementExists(SettlementTypeEnum.CONSUMPTION.getCode(), consumptionRecordId, consumptionRecordId)) {
            throw new BizException(40900, "该消耗记录已结算");
        }

        String no = generateSettlementNo("CNS");
        SettlementRecord record = new SettlementRecord();
        record.setSettlementNo(no);
        record.setSettlementType(SettlementTypeEnum.CONSUMPTION.getCode());
        record.setSourceId(consumptionRecordId);
        record.setSourceItemId(consumptionRecordId);
        record.setSourceNo("CNS-" + source.getId());
        record.setBatchNo(source.getBatchNo());
        record.setReagentId(source.getReagentId());
        record.setQuantity(source.getQuantity());
        record.setUnitPrice(source.getUnitPrice());
        record.setAmount(source.getAmount());
        record.setStatus("ACTIVE");
        record.setSettledBy(operatorId);
        record.setSettledAt(LocalDateTime.now());
        insertSettlement(record, record.getSourceNo());
        log.info("消耗结算: no={}, consumptionRecordId={}, amount={}", no, consumptionRecordId, record.getAmount());
    }

    @Override
    public void reverseSettlement(Long settlementId, String reason) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        SettlementRecord record = recordMapper.selectById(settlementId);
        if (record == null) throw new BizException(40400, "结算记录不存在");
        if (SettlementStatusEnum.REVERSED.getCode().equals(record.getStatus())) throw new BizException(40001, "该记录已被冲销，不能重复冲销");

        record.setStatus(SettlementStatusEnum.REVERSED.getCode());
        record.setReversedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.info("冲销结算: id={}, reason={}, operatorId={}", settlementId, reason, operatorId);
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", recordMapper.selectCount(null));
        stats.put("active", recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getStatus, "ACTIVE")));
        stats.put("reversed", recordMapper.selectCount(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getStatus, SettlementStatusEnum.REVERSED.getCode())));
        return stats;
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
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BizException(40900, "来源单据已结算: " + sourceNo);
        }
    }

    // ── 消耗结算明细（消耗数据全量字段 JOIN）──

    private static final String CONSUMPTION_DETAIL_SQL = """
            SELECT
                sr.settlement_no,
                sr.id AS settlement_id,
                cr.id AS consumption_record_id,
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
            Long reagentId, Long deviceId, Long warehouseId) {
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

        // Count total
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        // Paginated query
        sql.append(" ORDER BY cr.occurred_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);

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

        return new PageVO<>(total != null ? total : 0L, page, pageSize, records);
    }
}
