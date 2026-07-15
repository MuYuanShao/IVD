package com.zlth.ivd.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.SettlementStatusEnum;
import com.zlth.ivd.common.enums.SettlementTypeEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.mapper.SettlementRecordMapper;
import com.zlth.ivd.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.IdUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRecordMapper recordMapper;
    private final ConsumptionRecordMapper consumptionRecordMapper;

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
    public void settleRequisition(Long requisitionId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // TODO: 从采购申领单读取真实 quantity/amount/batchNo/reagentId
        //       当前 settlement 模块未依赖 ivd-procurement，跨模块 Service 注入待落实
        log.warn("申领结算暂用占位数据(quantity=1, amount=0)，待接入采购申领单真实数据: requisitionId={}", requisitionId);

        String no = generateSettlementNo("REQ");
        SettlementRecord record = new SettlementRecord();
        record.setSettlementNo(no);
        record.setSettlementType(SettlementTypeEnum.REQUISITION.getCode());
        record.setSourceId(requisitionId);
        record.setQuantity(BigDecimal.ONE);
        record.setAmount(BigDecimal.ZERO);
        record.setStatus("ACTIVE");
        record.setSettledBy(operatorId);
        record.setSettledAt(LocalDateTime.now());
        recordMapper.insert(record);
        log.info("申领结算: no={}, requisitionId={}", no, requisitionId);
    }

    @Override
    public void settleConsumption(Long consumptionRecordId) {
        Integer operatorId = SecurityUtils.getCurrentUserId().intValue();
        // 从消耗记录读取真实数据
        ConsumptionRecord source = consumptionRecordMapper.selectById(consumptionRecordId);
        if (source == null) {
            log.warn("消耗记录不存在: consumptionRecordId={}，使用占位数据", consumptionRecordId);
        }

        String no = generateSettlementNo("CNS");
        SettlementRecord record = new SettlementRecord();
        record.setSettlementNo(no);
        record.setSettlementType(SettlementTypeEnum.CONSUMPTION.getCode());
        record.setSourceId(consumptionRecordId);
        record.setSourceNo(source != null ? "CNS-" + source.getId() : null);
        record.setBatchNo(source != null ? source.getBatchNo() : null);
        record.setReagentId(source != null ? source.getReagentId() : null);
        record.setQuantity(source != null ? source.getQuantity() : BigDecimal.ONE);
        record.setUnitPrice(source != null ? source.getUnitPrice() : null);
        record.setAmount(source != null ? source.getAmount() : BigDecimal.ZERO);
        record.setStatus("ACTIVE");
        record.setSettledBy(operatorId);
        record.setSettledAt(LocalDateTime.now());
        recordMapper.insert(record);
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
        return "STL-" + prefix + "-" + IdUtil.getSnowflakeNextIdStr();
    }
}
