package com.zlth.ivd.laboratory.service.impl;
import com.zlth.ivd.laboratory.dto.ResultEvidenceRequest;
import com.zlth.ivd.laboratory.vo.ResultEvidenceVO;
import com.zlth.ivd.laboratory.service.ResultEvidenceService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.service.BatchEvidenceDirectoryService;
import com.zlth.ivd.consumption.service.ConsumptionEvidenceService;
import com.zlth.ivd.laboratory.entity.LabResultEvidence;
import com.zlth.ivd.laboratory.mapper.LabResultEvidenceMapper;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.LabResultService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED,rollbackFor=Exception.class)
public class ResultEvidenceServiceImpl implements ResultEvidenceService {
    private final LabResultEvidenceMapper mapper;
    private final LabResultService results;
    private final BatchEvidenceDirectoryService batches;
    private final ConsumptionEvidenceService consumption;
    private final ClinicalRecordSupport records;
    public record StoredEvidence(Long resultId,int resultVersion,BatchEvidenceDirectoryService.BatchEvidence batch,ConsumptionEvidenceService.ConsumptionEvidence consumption) { }
    public com.zlth.ivd.common.dto.PageVO<com.zlth.ivd.laboratory.vo.ClinicalEvidenceTraceVO> trace(Long batchId,Long consumptionId,int page,int size){
        var scope=ClinicalAccess.requireClinical();if(batchId==null && consumptionId==null)throw new BizException(40001,"请提供真实批次 ID 或消耗 ID");
        if(batchId!=null)batches.requireEvidence(batchId,null,null,LocalDateTime.now());
        var q=new LambdaQueryWrapper<LabResultEvidence>().eq(LabResultEvidence::getInstitutionId,scope.institutionId()).eq(LabResultEvidence::getDepartmentId,scope.departmentId());if(batchId!=null)q.eq(LabResultEvidence::getBatchId,batchId);if(consumptionId!=null)q.eq(LabResultEvidence::getConsumptionId,consumptionId);
        q.exists("SELECT 1 FROM lab_result r JOIN lab_sample s ON s.id=r.sample_id AND s.institution_id=r.institution_id JOIN lab_patient_department g ON g.patient_id=s.patient_id AND g.institution_id=s.institution_id WHERE r.id=lab_result_evidence.result_id AND r.institution_id="+scope.institutionId()+" AND s.department_id="+scope.departmentId()+" AND g.department_id="+scope.departmentId()+" AND g.active=1");
        var recordsPage=mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<LabResultEvidence>(Math.max(1,page),Math.min(100,Math.max(1,size))),q.orderByDesc(LabResultEvidence::getId));
        var output=recordsPage.getRecords().stream().map(row->{var historical=results.versions(row.getResultId()).stream().filter(result->Objects.equals(result.version(),row.getResultVersion())).findFirst().orElseThrow(()->new BizException(40900,"证据所引用结果版本缺失"));return new com.zlth.ivd.laboratory.vo.ClinicalEvidenceTraceVO(historical,vo(row));}).toList();records.audit("laboratory-evidence","TRACE",null);return new com.zlth.ivd.common.dto.PageVO<>(recordsPage.getTotal(),recordsPage.getCurrent(),recordsPage.getSize(),output);
    }
    public List<BatchEvidenceDirectoryService.BatchEvidence> batchOptions(Long id,String keyword){var result=results.detail(id);return batches.options(result.deviceId(),result.testedAt(),keyword);}
    public List<ConsumptionEvidenceService.ConsumptionEvidence> consumptionOptions(Long id,Long batchId){var result=results.detail(id);batches.requireEvidence(batchId,null,null,result.testedAt());return consumption.options(batchId,result.deviceId(),result.testedAt());}
    public ResultEvidenceVO bind(Long resultId,ResultEvidenceRequest request){
        var scope=ClinicalAccess.requireClinical();records.validate(request);var result=results.lockContext(resultId).result();
        results.requireEditable(resultId);
        if(!Objects.equals(result.version(),request.resultVersion()))throw new BizException(40900,"结果版本已变更，不能给旧版本追加证据");
        if(result.deviceId()!=null && request.deviceLoadId()==null)throw new BizException(40001,"仪器检测须引用实际上机记录");
        if(result.deviceId()==null && request.deviceLoadId()!=null)throw new BizException(40001,"无本地设备不能引用上机记录");
        var batch=batches.requireEvidence(request.batchId(),request.deviceLoadId(),result.deviceId(),result.testedAt());
        var consumed=request.consumptionId()==null?null:consumption.requireEvidence(request.consumptionId(),batch.batchId(),result.deviceId(),result.testedAt());
        if(consumed!=null && !Objects.equals(consumed.reagentId(),batch.reagentId()))throw new BizException(40900,"消耗试剂与批次试剂不一致");
        String key=records.index("result-evidence",resultId+"|"+result.version()+"|"+request.batchId()+"|"+request.deviceLoadId()+"|"+request.consumptionId());
        var existing=mapper.selectOne(query(resultId).eq(LabResultEvidence::getEvidenceKey,key));
        if(existing!=null){records.audit("laboratory-evidence","REPLAY",existing.getId());return vo(existing);}
        if(mapper.selectCount(query(resultId).eq(LabResultEvidence::getResultVersion,result.version()))>=100)throw new BizException(40001,"单份结果证据最多100条");
        var value=new LabResultEvidence();value.setDepartmentId(scope.departmentId());value.setResultId(resultId);value.setResultVersion(result.version());value.setBatchId(request.batchId());value.setDeviceLoadId(request.deviceLoadId());value.setConsumptionId(request.consumptionId());value.setEvidenceKey(key);
        value.setEncryptedSnapshot(records.encrypt(new StoredEvidence(resultId,result.version(),batch,consumed)));value.setActorId(scope.actorId());value.setCreatedAt(LocalDateTime.now());mapper.insert(value);
        records.audit("laboratory-evidence","BIND",value.getId());return vo(value);
    }
    public List<ResultEvidenceVO> list(Long resultId,Integer version){
        var current=results.detail(resultId);int selected=version==null?current.version():version;
        if(selected<0 || selected>current.version())throw new BizException(40400,"结果版本不存在");
        var output=mapper.selectList(query(resultId).eq(LabResultEvidence::getResultVersion,selected).orderByAsc(LabResultEvidence::getId)).stream().map(this::vo).toList();
        records.audit("laboratory-evidence","LIST",resultId);return output;
    }
    private LambdaQueryWrapper<LabResultEvidence> query(Long id){var scope=ClinicalAccess.requireClinical();return new LambdaQueryWrapper<LabResultEvidence>().eq(LabResultEvidence::getInstitutionId,scope.institutionId()).eq(LabResultEvidence::getDepartmentId,scope.departmentId()).eq(LabResultEvidence::getResultId,id);}
    private ResultEvidenceVO vo(LabResultEvidence value){
        var snapshot=records.decrypt(value.getEncryptedSnapshot(),StoredEvidence.class);
        if(!Objects.equals(snapshot.resultId(),value.getResultId()) || snapshot.resultVersion()!=value.getResultVersion() || !Objects.equals(snapshot.batch().batchId(),value.getBatchId())
                || !Objects.equals(snapshot.batch().deviceLoadId(),value.getDeviceLoadId()) || !Objects.equals(snapshot.consumption()==null?null:snapshot.consumption().id(),value.getConsumptionId()))throw new BizException(40900,"证据关联完整性校验失败");
        return new ResultEvidenceVO(value.getId(),value.getResultId(),value.getResultVersion(),snapshot.batch(),snapshot.consumption(),value.getActorId());
    }
}
