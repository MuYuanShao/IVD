package com.zlth.ivd.consumption.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.ConsumptionPurposeEnum;
import com.zlth.ivd.common.enums.ConsumptionRecordTypeEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.consumption.service.ConsumptionService;
import com.zlth.ivd.consumption.vo.ConsumptionRecordVO;
import com.zlth.ivd.inventory.entity.Device;
import com.zlth.ivd.inventory.entity.DeviceBatchLoad;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.udi.service.UniqueCodeService;
import com.zlth.ivd.udi.service.UdiStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ConsumptionServiceImpl implements ConsumptionService {

    private final ConsumptionRecordMapper recordMapper;
    private final com.zlth.ivd.inventory.service.ConsumptionInventoryService inventory;
    private final com.zlth.ivd.catalog.service.CatalogReferenceService catalogs;
    private final UniqueCodeService uniqueCodeService;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final com.zlth.ivd.common.service.StockBillingStatusService billing;
    private final com.zlth.ivd.inventory.service.InstrumentUsageService instrumentUsageService;

    @Override
    public PageVO<ConsumptionRecordVO> listRecords(int page, int pageSize, String recordType, Long deviceId, Long reagentId) {
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        if (recordType != null) qw.eq(ConsumptionRecord::getRecordType, recordType);
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        if (reagentId != null) qw.eq(ConsumptionRecord::getReagentId, reagentId);
        qw.orderByDesc(ConsumptionRecord::getOccurredAt);
        Page<ConsumptionRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
        var billingStatuses=billing.consumptionStatuses(p.getRecords().stream().map(ConsumptionRecord::getId).toList());

        // Batch fetch device names
        Set<Long> deviceIds = p.getRecords().stream().map(ConsumptionRecord::getDeviceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deviceNameMap = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<Device> devices = inventory.getDevices(deviceIds);
            for (Device d : devices) deviceNameMap.put(d.getId(), d.getName());
        }

        // Batch fetch reagent names
        Set<Long> reagentIds = p.getRecords().stream().map(ConsumptionRecord::getReagentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> reagentNameMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogRows = catalogs.findByIds(reagentIds);
            for (ReagentCatalog c : catalogRows) reagentNameMap.put(c.getId(), c.getGenericName());
        }

        // Build VOs
        List<ConsumptionRecordVO> records = new ArrayList<>();
        for (ConsumptionRecord r : p.getRecords()) {
            ConsumptionRecordVO vo = new ConsumptionRecordVO();
            vo.setId(r.getId());
            vo.setBatchId(r.getBatchId());vo.setSettlementMode(r.getSettlementMode());
            var billed=billingStatuses.get(r.getId());
            vo.setSettlementNo(billed==null?null:billed.settlementNo());
            vo.setSettlementStatus(billed==null?"UNSETTLED":billed.status());
            vo.setRecordType(r.getRecordType());
            vo.setBatchNo(r.getBatchNo());
            vo.setReagentId(r.getReagentId());
            vo.setReagentName(r.getReagentId() != null ? reagentNameMap.getOrDefault(r.getReagentId(), "试剂#" + r.getReagentId()) : null);
            vo.setMaterialType(r.getMaterialType());
            vo.setDeviceId(r.getDeviceId());
            vo.setDeviceName(r.getDeviceId() != null ? deviceNameMap.getOrDefault(r.getDeviceId(), "设备#" + r.getDeviceId()) : null);
            vo.setQuantity(r.getQuantity());
            vo.setUnit(r.getUnit());
            vo.setUnitPrice(r.getUnitPrice());
            vo.setAmount(r.getAmount());
            vo.setPurpose(r.getPurpose());
            vo.setWarehouseId(r.getWarehouseId());
            vo.setOperatorId(r.getOperatorId());
            vo.setRemark(r.getRemark());
            vo.setOccurredAt(r.getOccurredAt());
            vo.setCreatedAt(r.getCreatedAt());
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public void recordConsumption(Long batchId, Long deviceId, BigDecimal qty, String purpose, String remark) {
        requirePositiveQuantity(qty);
        Long operatorId = SecurityUtils.getCurrentOperatorIdOrNull();
        ReagentBatch selected=inventory.getBatch(batchId);
        if(selected==null) throw new BizException(40400,"库存批次不存在");
        ReagentCatalog catalog=catalogs.findById(selected.getReagentId());
        if(catalog==null) throw new BizException(40400,"试剂目录不存在");
        if(catalog.getDeviceBindMode()==com.zlth.ivd.common.enums.DeviceBindModeEnum.FORCE && deviceId==null) throw new BizException(40001,"该试剂必须绑定仪器后消耗");
        if(Integer.valueOf(1).equals(catalog.getEnableUniqueCode()) && qty.stripTrailingZeros().scale()>0) throw new BizException(40001,"唯一码管控试剂须按完整单位消耗");
        if(deviceId!=null) instrumentUsageService.bind(batchId,deviceId);
        // 带乐观锁重试的库存扣减
        ReagentBatch batch = inventory.consumeStock(batchId, qty);

        // 写入消耗记录
        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType(ConsumptionRecordTypeEnum.CONSUME.getCode());
        record.setBatchNo(batch.getBatchNo());
        record.setBatchId(batch.getId());
        record.setPurchaseOrderItemId(batch.getOriginPurchaseOrderItemId());
        record.setSettlementMode(batch.getSettlementMode());
        record.setUnit(batch.getUnit()!=null ? batch.getUnit() : catalog.getUnit());
        record.setMaterialType(catalog.getMaterialType().name());
        record.setReagentId(batch.getReagentId());
        // 结算库房 = 操作人所在科室的二级库；若操作人无科室或无对应二级库，回退到批次所在库房
        record.setWarehouseId(batch.getWarehouseId());
        record.setDeviceId(deviceId);
        record.setQuantity(qty);
        record.setUnitPrice(batch.getUnitPrice());
        record.setAmount(batch.getUnitPrice() != null ? batch.getUnitPrice().multiply(qty) : null);
        record.setPurpose(purpose != null ? purpose : ConsumptionPurposeEnum.ROUTINE.getCode());
        record.setOperatorId(operatorId);
        record.setRemark(remark);
        record.setOccurredAt(LocalDateTime.now());
        recordMapper.insert(record);
        log.info("消耗记录: batchId={}, deviceId={}, qty={}, purpose={}", batchId, deviceId, qty, purpose);

        // 唯一码全流程流转：标记已消耗
        int codeCount = qty.intValue();
        if (codeCount > 0) {
            int consumed=uniqueCodeService.consumeUniqueCodes(batchId, codeCount);
            if(Integer.valueOf(1).equals(catalog.getEnableUniqueCode()) && consumed!=codeCount)
                throw new BizException(40900,"可用唯一码数量与消耗数量不一致，操作已回滚");
        }
        events.publishEvent(new com.zlth.ivd.common.event.StockBusinessRecordedEvent(record.getId(),record.getRecordType()));
    }

    /**
     * 扣减批次库存（乐观锁重试，最多 3 次）
     */
    @Override
    public void recordInbound(Long batchId, BigDecimal qty, Long warehouseId) {
        requirePositiveQuantity(qty);
        Long operatorId = SecurityUtils.getCurrentOperatorIdOrNull();
        // 带乐观锁重试的库存增加
        ReagentBatch batch = inventory.addManualStock(batchId, qty, warehouseId);

        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType(ConsumptionRecordTypeEnum.INBOUND.getCode());
        record.setBatchNo(batch.getBatchNo());
        record.setBatchId(batch.getId());
        record.setUnit(batch.getUnit());
        record.setReagentId(batch.getReagentId());
        record.setQuantity(qty);
        record.setUnitPrice(batch.getUnitPrice());
        record.setAmount(batch.getUnitPrice() != null ? batch.getUnitPrice().multiply(qty) : null);
        record.setWarehouseId(warehouseId);
        record.setOperatorId(operatorId);
        record.setOccurredAt(LocalDateTime.now());
        recordMapper.insert(record);
        log.info("入库记录: batchId={}, qty={}, warehouseId={}", batchId, qty, warehouseId);
    }

    /**
     * 增加批次库存（乐观锁重试，最多 3 次）
     */
    @Override
    public void batchConsume(String batchNo, Long deviceId, BigDecimal qty, String purpose, String remark) {
        requirePositiveQuantity(qty);
        if(deviceId==null) {
            List<ReagentBatch> matches=inventory.availableByBatchNo(batchNo);
            if(matches.size()!=1) throw new BizException(40900,"该批号无唯一可用库存，请选择具体库存批次");
            recordConsumption(matches.get(0).getId(),null,qty,purpose,remark);return;
        }
        Device device = inventory.getDevice(deviceId);
        if (device == null || !Boolean.TRUE.equals(device.getIsActive())) {
            throw new BizException(40400, "设备不存在或已停用: " + deviceId);
        }
        if (device.getLocationId() == null) {
            throw new BizException(40001, "设备未绑定所属仓库: " + deviceId);
        }
        var matching=inventory.availableForDevice(batchNo,device.getLocationId(),qty);
        if(matching.size()>1)throw new BizException(40900,"批号存在多个采购来源，请选择明确库存批次");
        ReagentBatch batch=matching.stream().findFirst().orElse(null);
        if (batch == null) {
            throw new BizException(40001, "设备所属仓库无该批次可用库存: " + batchNo);
        }
        recordConsumption(batch.getId(), deviceId, qty,
                purpose != null ? purpose : ConsumptionPurposeEnum.BATCH_TEST.getCode(),
                remark != null ? remark : "批量核销");
    }

    private void requirePositiveQuantity(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0 || qty.stripTrailingZeros().scale()>2) {
            throw new BizException(40001, "数量必须大于0");
        }
    }

    @Override
    public Map<String, Object> getStats(Long deviceId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(ConsumptionRecord::getRecordType,ConsumptionRecordTypeEnum.CONSUME.getCode());
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        stats.put("totalRecords", recordMapper.selectCount(qw));
        stats.put("todayConsume", recordMapper.selectCount(
            new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRecordType, ConsumptionRecordTypeEnum.CONSUME.getCode())
                .ge(ConsumptionRecord::getOccurredAt, LocalDateTime.now().toLocalDate().atStartOfDay())
        ));

        // activeDevices: count of devices where isActive = true
        try {
            stats.put("activeDevices", inventory.activeDeviceCount());
        } catch (Exception e) { stats.put("activeDevices", 0L); }

        // pendingWriteoff: count of device_batch_load records where unloadedAt IS NULL (batch still loaded)
        try {
            stats.put("pendingWriteoff", inventory.openLoadCount());
        } catch (Exception e) { stats.put("pendingWriteoff", 0L); }

        return stats;
    }
}
