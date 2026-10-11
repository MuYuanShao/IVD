package com.zlth.ivd.inventory.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.mapper.*;
import com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class BatchEvidenceDirectoryServiceImpl implements BatchEvidenceDirectoryService {
    private final ReagentBatchMapper batches;
    private final DeviceBatchLoadMapper loads;
    public java.util.List<BatchEvidence> options(Long deviceId,LocalDateTime testedAt,String keyword){
        long owner=InstitutionContext.require().institutionId();
        if(deviceId==null){var query=new QueryWrapper<ReagentBatch>().eq("institution_id",owner);if(keyword!=null && !keyword.isBlank())query.like("batch_no",keyword.trim());return batches.selectList(query.orderByDesc("id").last("LIMIT 100")).stream().map(batch->requireEvidence(batch.getId(),null,null,testedAt)).toList();}
        var query=new QueryWrapper<DeviceBatchLoad>().eq("institution_id",owner).eq("device_id",deviceId).le("load_time",testedAt).and(w->w.isNull("unload_time").or().gt("unload_time",testedAt));if(keyword!=null && !keyword.isBlank())query.like("batch_no",keyword.trim());
        var output=new java.util.ArrayList<BatchEvidence>();for(var load:loads.selectList(query.orderByDesc("id").last("LIMIT 100"))){if(load.getBatchId()==null)continue;try{output.add(requireEvidence(load.getBatchId(),load.getId(),deviceId,testedAt));}catch(BizException invalid){/* Historical ambiguous references are not selectable. */}}
        return java.util.List.copyOf(output);
    }
    public BatchEvidence requireEvidence(Long batchId,Long loadId,Long deviceId,LocalDateTime testedAt){
        var scope=InstitutionContext.require();
        var batch=batches.selectOne(new QueryWrapper<ReagentBatch>().eq("id",batchId).eq("institution_id",scope.institutionId()));
        if(batch==null)throw new BizException(40400,"批次不存在或无权访问");
        DeviceBatchLoad load=null;
        if(loadId!=null){
            load=loads.selectOne(new QueryWrapper<DeviceBatchLoad>().eq("id",loadId).eq("institution_id",scope.institutionId()));
            if(load==null || !Objects.equals(load.getBatchId(),batchId) || !Objects.equals(load.getDeviceId(),deviceId)
                    || !Objects.equals(load.getReagentId(),batch.getReagentId()) || !Objects.equals(load.getBatchNo(),batch.getBatchNo()))
                throw new BizException(40900,"上机证据与实际批次或设备不一致，历史仅有批号的记录不能自动匹配");
            if(testedAt==null || load.getLoadedAt()==null || testedAt.isBefore(load.getLoadedAt()) || (load.getUnloadedAt()!=null && !testedAt.isBefore(load.getUnloadedAt())))
                throw new BizException(40900,"检测时间不在上机记录的有效时间段内");
        }
        return new BatchEvidence(batchId,batch.getBatchNo(),batch.getReagentId(),batch.getWarehouseId(),batch.getUnit(),loadId,deviceId,load==null?null:load.getLoadedAt(),load==null?null:load.getUnloadedAt());
    }
}
