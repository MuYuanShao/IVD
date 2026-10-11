package com.zlth.ivd.consumption.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.consumption.entity.ConsumptionRecord;
import com.zlth.ivd.consumption.mapper.ConsumptionRecordMapper;
import com.zlth.ivd.consumption.service.ConsumptionEvidenceService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ConsumptionEvidenceServiceImpl implements ConsumptionEvidenceService {
    private final ConsumptionRecordMapper mapper;
    public java.util.List<ConsumptionEvidence> options(Long batchId,Long deviceId,LocalDateTime testedAt){
        var query=new QueryWrapper<ConsumptionRecord>().eq("institution_id",InstitutionContext.require().institutionId()).eq("batch_id",batchId).eq("record_type","CONSUME").gt("quantity",0);if(deviceId==null)query.isNull("device_id");else query.eq("device_id",deviceId);
        return mapper.selectList(query.orderByDesc("id").last("LIMIT 100")).stream().map(row->requireEvidence(row.getId(),batchId,deviceId,testedAt)).toList();
    }
    public ConsumptionEvidence requireEvidence(Long id,Long batchId,Long deviceId,LocalDateTime testedAt){
        var row=mapper.selectOne(new QueryWrapper<ConsumptionRecord>().eq("id",id).eq("institution_id",InstitutionContext.require().institutionId()));
        if(row==null)throw new BizException(40400,"消耗证据不存在或无权访问");
        if(!"CONSUME".equals(row.getRecordType()) || !Objects.equals(batchId,row.getBatchId()) || !Objects.equals(deviceId,row.getDeviceId())
                || row.getQuantity()==null || row.getQuantity().signum()<=0 || row.getOccurredAt()==null || testedAt==null)
            throw new BizException(40900,"消耗证据不是正向消耗或与批次、设备不一致；不得按历史批号推断");
        return new ConsumptionEvidence(row.getId(),row.getBatchId(),row.getReagentId(),row.getDeviceId(),row.getWarehouseId(),row.getQuantity(),row.getUnit(),row.getAmount(),row.getOccurredAt());
    }
}
