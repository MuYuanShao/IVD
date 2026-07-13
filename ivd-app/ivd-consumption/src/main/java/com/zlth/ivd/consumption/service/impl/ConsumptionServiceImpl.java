package com.zlth.ivd.consumption.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.consumption.service.ConsumptionService;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import com.zlth.ivd.inventory.mapper.ReagentBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ConsumptionServiceImpl implements ConsumptionService {

    private final ConsumptionRecordMapper recordMapper;
    private final ReagentBatchMapper batchMapper;

    @Override
    public PageVO<ConsumptionRecord> listRecords(int page, int pageSize, String recordType, Long deviceId, Long reagentId) {
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        if (recordType != null) qw.eq(ConsumptionRecord::getRecordType, recordType);
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        if (reagentId != null) qw.eq(ConsumptionRecord::getReagentId, reagentId);
        qw.orderByDesc(ConsumptionRecord::getOccurredAt);
        Page<ConsumptionRecord> p = recordMapper.selectPage(new Page<>(page, pageSize), qw);
        return new PageVO<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    public void recordConsumption(Long batchId, Long deviceId, BigDecimal qty, String purpose, String remark) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 带乐观锁重试的库存扣减
        ReagentBatch batch = consumeBatchStock(batchId, qty);

        // 写入消耗记录
        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType("CONSUME");
        record.setBatchNo(batch.getBatchNo());
        record.setReagentId(batch.getReagentId());
        record.setDeviceId(deviceId);
        record.setQuantity(qty);
        record.setUnitPrice(batch.getUnitPrice());
        record.setAmount(batch.getUnitPrice() != null ? batch.getUnitPrice().multiply(qty) : null);
        record.setPurpose(purpose != null ? purpose : "ROUTINE");
        record.setOperatorId(operatorId);
        record.setRemark(remark);
        record.setOccurredAt(LocalDateTime.now());
        recordMapper.insert(record);
        log.info("消耗记录: batchId={}, deviceId={}, qty={}, purpose={}", batchId, deviceId, qty, purpose);
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
            } else {
                batch.setUdiStatus(UdiStatusEnum.IN_USE);
            }

            try {
                batchMapper.updateById(batch);
                return batch;
            } catch (MybatisPlusException e) {
                if (i == maxRetries - 1) throw e;
                log.warn("库存扣减乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
            }
        }
        throw new BizException(50000, "库存扣减失败：超过最大重试次数");
    }

    @Override
    public void recordInbound(Long batchId, BigDecimal qty, Long warehouseId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 带乐观锁重试的库存增加
        ReagentBatch batch = inboundBatchStock(batchId, qty, warehouseId);

        ConsumptionRecord record = new ConsumptionRecord();
        record.setRecordType("INBOUND");
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

            try {
                batchMapper.updateById(batch);
                return batch;
            } catch (MybatisPlusException e) {
                if (i == maxRetries - 1) throw e;
                log.warn("入库库存乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
            }
        }
        throw new BizException(50000, "入库库存更新失败：超过最大重试次数");
    }

    @Override
    public void batchConsume(String batchNo, Long deviceId, BigDecimal qty) {
        ReagentBatch batch = batchMapper.selectOne(
            new LambdaQueryWrapper<ReagentBatch>().eq(ReagentBatch::getBatchNo, batchNo)
        );
        if (batch == null) throw new BizException(40400, "批次不存在: " + batchNo);
        recordConsumption(batch.getId(), deviceId, qty, "BATCH_TEST", "批量核销");
    }

    @Override
    public Map<String, Object> getStats(Long deviceId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        LambdaQueryWrapper<ConsumptionRecord> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) qw.eq(ConsumptionRecord::getDeviceId, deviceId);
        stats.put("totalRecords", recordMapper.selectCount(qw));
        stats.put("todayConsume", recordMapper.selectCount(
            new LambdaQueryWrapper<ConsumptionRecord>()
                .eq(ConsumptionRecord::getRecordType, "CONSUME")
                .ge(ConsumptionRecord::getOccurredAt, LocalDateTime.now().toLocalDate().atStartOfDay())
        ));
        return stats;
    }
}
