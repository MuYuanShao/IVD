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
