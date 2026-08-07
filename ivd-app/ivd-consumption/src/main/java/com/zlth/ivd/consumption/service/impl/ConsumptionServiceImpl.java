package com.zlth.ivd.consumption.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
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
import com.zlth.ivd.inventory.mapper.DeviceBatchLoadMapper;
import com.zlth.ivd.inventory.mapper.DeviceMapper;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import com.zlth.ivd.udi.service.UniqueCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final ReagentBatchMapper batchMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceBatchLoadMapper loadMapper;
    private final ReagentCatalogMapper catalogMapper;
    private final UniqueCodeService uniqueCodeService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageVO<ConsumptionRecordVO> listRecords(int page, int pageSize, String recordType, Long deviceId, Long reagentId) {
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        if (recordType != null) qw.eq(ConsumptionRecord::getRecordType, recordType);
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        if (reagentId != null) qw.eq(ConsumptionRecord::getReagentId, reagentId);
        qw.orderByDesc(ConsumptionRecord::getOccurredAt);
        Page<ConsumptionRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);

        // Batch fetch device names
        Set<Long> deviceIds = p.getRecords().stream().map(ConsumptionRecord::getDeviceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deviceNameMap = new HashMap<>();
        if (!deviceIds.isEmpty()) {
            List<Device> devices = deviceMapper.selectBatchIds(deviceIds);
            for (Device d : devices) deviceNameMap.put(d.getId(), d.getName());
        }

        // Batch fetch reagent names
        Set<Long> reagentIds = p.getRecords().stream().map(ConsumptionRecord::getReagentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> reagentNameMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(reagentIds);
            for (ReagentCatalog c : catalogs) reagentNameMap.put(c.getId(), c.getGenericName());
        }

        // Build VOs
        List<ConsumptionRecordVO> records = new ArrayList<>();
        for (ConsumptionRecord r : p.getRecords()) {
            ConsumptionRecordVO vo = new ConsumptionRecordVO();
            vo.setId(r.getId());
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
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 带乐观锁重试的库存扣减
        ReagentBatch batch = consumeBatchStock(batchId, qty);

        // 写入消耗记录
        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType(ConsumptionRecordTypeEnum.CONSUME.getCode());
        record.setBatchNo(batch.getBatchNo());
        record.setReagentId(batch.getReagentId());
        // 结算库房 = 操作人所在科室的二级库；若操作人无科室或无对应二级库，回退到批次所在库房
        record.setWarehouseId(resolveSettlementWarehouseId(operatorId, batch.getWarehouseId()));
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
            uniqueCodeService.consumeUniqueCodes(batchId, codeCount);
        }
    }

    /**
     * 扣减批次库存（乐观锁重试，最多 3 次）
     */
    private ReagentBatch consumeBatchStock(Long batchId, BigDecimal qty) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch batch = batchMapper.selectById(batchId);
            if (batch == null) throw new BizException(40400, "批次不存在");
            if (batch.getStock().compareTo(qty) < 0) throw new BizException(40001, "库存不足");

            batch.setStock(batch.getStock().subtract(qty));
            if (batch.getStock().compareTo(BigDecimal.ZERO) <= 0) {
                batch.setUdiStatus(UdiStatusEnum.CONSUMED);
            }
            // 部分消耗后仍有库存 → 保持 IN_STOCK，不改为 IN_USE（IN_USE 仅用于设备装载场景）

            int updated = batchMapper.updateById(batch);
            if (updated > 0) {
                return batch;
            }
            if (i == maxRetries - 1) {
                throw new BizException(50000, "库存扣减失败：超过最大重试次数");
            }
            log.warn("库存扣减乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
        }
        throw new BizException(50000, "库存扣减失败：超过最大重试次数");
    }

    /**
     * 消耗结算库房 = 操作人所在科室的二级库（level=2）
     * 若操作人无科室或科室无对应二级库，则回退到批次所在库房
     */
    private Long resolveSettlementWarehouseId(Integer operatorId, Long fallbackWarehouseId) {
        try {
            String sql = """
                SELECT w.id FROM warehouse w
                INNER JOIN sys_user u ON w.dept_id = u.dept_id
                WHERE u.id = ? AND w.level = 2 AND w.status = 1
                LIMIT 1
                """;
            List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, operatorId);
            if (ids != null && !ids.isEmpty()) {
                log.debug("消耗结算库房: 操作人 userId={} → 二级库 warehouseId={}", operatorId, ids.get(0));
                return ids.get(0);
            }
        } catch (Exception e) {
            log.warn("查询操作人二级库失败, userId={}, 回退到批次库房: {}", operatorId, e.getMessage());
        }
        return fallbackWarehouseId;
    }

    @Override
    public void recordInbound(Long batchId, BigDecimal qty, Long warehouseId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 带乐观锁重试的库存增加
        ReagentBatch batch = inboundBatchStock(batchId, qty, warehouseId);

        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType(ConsumptionRecordTypeEnum.INBOUND.getCode());
        record.setBatchNo(batch.getBatchNo());
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
    private ReagentBatch inboundBatchStock(Long batchId, BigDecimal qty, Long warehouseId) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch batch = batchMapper.selectById(batchId);
            if (batch == null) throw new BizException(40400, "批次不存在");
            batch.setStock(batch.getStock().add(qty));
            batch.setUdiStatus(UdiStatusEnum.IN_STOCK);
            batch.setWarehouseId(warehouseId);

            int updated = batchMapper.updateById(batch);
            if (updated > 0) {
                return batch;
            }
            if (i == maxRetries - 1) {
                throw new BizException(50000, "入库库存更新失败：超过最大重试次数");
            }
            log.warn("入库库存乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
        }
        throw new BizException(50000, "入库库存更新失败：超过最大重试次数");
    }

    @Override
    public void batchConsume(String batchNo, Long deviceId, BigDecimal qty) {
        ReagentBatch batch = batchMapper.selectOne(
            new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getBatchNo, batchNo)
        );
        if (batch == null) throw new BizException(40400, "批次不存在: " + batchNo);
        recordConsumption(batch.getId(), deviceId, qty, ConsumptionPurposeEnum.BATCH_TEST.getCode(), "批量核销");
    }

    @Override
    public Map<String, Object> getStats(Long deviceId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        stats.put("totalRecords", recordMapper.selectCount(qw));
        stats.put("todayConsume", recordMapper.selectCount(
            new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRecordType, ConsumptionRecordTypeEnum.CONSUME.getCode())
                .ge(ConsumptionRecord::getOccurredAt, LocalDateTime.now().toLocalDate().atStartOfDay())
        ));

        // activeDevices: count of devices where isActive = true
        try {
            stats.put("activeDevices", deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getIsActive, true)));
        } catch (Exception e) { stats.put("activeDevices", 0L); }

        // pendingWriteoff: count of device_batch_load records where unloadedAt IS NULL (batch still loaded)
        try {
            stats.put("pendingWriteoff", loadMapper.selectCount(
                new LambdaQueryWrapper<DeviceBatchLoad>().isNull(DeviceBatchLoad::getUnloadedAt)));
        } catch (Exception e) { stats.put("pendingWriteoff", 0L); }

        return stats;
    }
}
