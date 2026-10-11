package com.zlth.ivd.settlement.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.service.StockBillingStatusService;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import com.zlth.ivd.settlement.mapper.SettlementRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class StockBillingStatusServiceImpl implements StockBillingStatusService {
    private final SettlementRecordMapper mapper;
    public Map<Long,BillingStatus> consumptionStatuses(Collection<Long> ids) {
        if(ids.isEmpty())return Map.of();
        var rows=mapper.selectList(new LambdaQueryWrapper<SettlementRecord>().eq(SettlementRecord::getSettlementType,"CONSUMPTION")
                .in(SettlementRecord::getSourceId,ids).orderByDesc(SettlementRecord::getSettledAt).orderByDesc(SettlementRecord::getId));
        var result=new HashMap<Long,BillingStatus>();
        for(var row:rows)result.putIfAbsent(row.getSourceId(),new BillingStatus(row.getSettlementNo(),row.getStatus()));
        return result;
    }
}
