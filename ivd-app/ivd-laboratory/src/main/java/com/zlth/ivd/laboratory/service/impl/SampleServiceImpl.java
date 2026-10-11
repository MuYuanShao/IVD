package com.zlth.ivd.laboratory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.enums.SampleStatusEnum;
import com.zlth.ivd.laboratory.event.SampleStatusChanged;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.*;
import com.zlth.ivd.laboratory.vo.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class SampleServiceImpl implements SampleService {
    private final LabSampleMapper samples;
    private final LabSampleEventMapper events;
    private final PatientService patients;
    private final ClinicalRecordSupport records;
    private final ApplicationEventPublisher publisher;
    public record StoredSample(Long patientId,Long departmentId,String number,String specimenType,LocalDateTime collectedAt) { }

    @Override public SampleDetailVO create(SampleRequest request){
        var scope=ClinicalAccess.requireClinical();records.validate(request);
        patients.requireAuthorizedForWrite(request.patientId());
        var value=new LabSample();value.setPatientId(request.patientId());value.setDepartmentId(scope.departmentId());
        value.setNumberIndex(records.index("sample-number",request.number()));
        value.setEncryptedDetails(records.encrypt(new StoredSample(value.getPatientId(),value.getDepartmentId(),request.number().trim(),request.specimenType().trim(),request.collectedAt())));
        value.setStatus(SampleStatusEnum.COLLECTED);value.setVersion(0);value.setCreatedBy(scope.actorId());
        value.setCreatedAt(LocalDateTime.now());value.setUpdatedAt(value.getCreatedAt());
        try{samples.insert(value);}catch(DuplicateKeyException error){throw new BizException(40900,"样本编号已存在，不会自动合并或覆盖");}
        appendEvent(value,null,null);records.audit("laboratory-sample","CREATE",value.getId());return vo(value);
    }
    @Override public SampleDetailVO requireNumber(String number){var row=samples.selectOne(authorizedQuery().eq(LabSample::getNumberIndex,records.index("sample-number",number)));if(row==null)throw new BizException(40400,"样本编号不存在或无权引用");return detail(row.getId());}
    @Override public SampleDetailVO detail(Long id){
        var value=owned(id,false);patients.detail(value.getPatientId());
        records.audit("laboratory-sample","DETAIL",id);return vo(value);
    }
    @Override public PageVO<SampleSummaryVO> list(int page,int size,Long patientId){
        var query=authorizedQuery();if(patientId!=null){patients.detail(patientId);query.eq(LabSample::getPatientId,patientId);}
        var result=samples.selectPage(new Page<LabSample>(Math.max(1,page),Math.min(100,Math.max(1,size))),query.orderByDesc(LabSample::getId));
        var summaries=result.getRecords().stream().map(value->{
            var payload=payload(value);String number=payload.number().length()>8?"****"+payload.number().substring(payload.number().length()-4):"****";
            return new SampleSummaryVO(value.getId(),value.getPatientId(),number,value.getStatus().getCode(),value.getVersion());
        }).toList();
        records.audit("laboratory-sample","LIST",null);return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),summaries);
    }
    @Override public SampleDetailVO receive(Long id,SampleActionRequest request){return transition(id,request,SampleStatusEnum.RECEIVED);}
    @Override public SampleDetailVO reject(Long id,SampleActionRequest request){return transition(id,request,SampleStatusEnum.REJECTED);}
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public SampleDetailVO requireReceivedForWrite(Long id){
        var initial=owned(id,false);patients.requireAuthorizedForWrite(initial.getPatientId());
        var value=owned(id,true);
        if(value.getStatus()!=SampleStatusEnum.RECEIVED) throw new BizException(40900,"样本尚未接收或已拒收，不能录入结果");
        records.audit("laboratory-sample","RESULT_ACCESS",id);return vo(value);
    }
    private SampleDetailVO transition(Long id,SampleActionRequest request,SampleStatusEnum target){
        records.validate(request);
        if(target==SampleStatusEnum.REJECTED && (request.reason()==null || request.reason().isBlank())) throw new BizException(40001,"拒收必须填写原因");
        var initial=owned(id,false);patients.requireAuthorizedForWrite(initial.getPatientId());
        var value=owned(id,true);
        if(!Objects.equals(value.getVersion(),request.version())) throw new BizException(40900,"样本版本已变更，请刷新后重试");
        var from=value.getStatus();from.validateTransition(target);value.setStatus(target);value.setUpdatedAt(LocalDateTime.now());
        if(target==SampleStatusEnum.RECEIVED)value.setReceivedAt(value.getUpdatedAt());
        if(samples.updateById(value)!=1)throw new BizException(40900,"样本版本已变更，请刷新后重试");
        appendEvent(value,from,request.reason());records.audit("laboratory-sample",target.getCode(),id);
        publisher.publishEvent(new SampleStatusChanged(InstitutionContext.require().institutionId(),value.getDepartmentId(),id,from,target,InstitutionContext.requireUserActor()));
        return vo(value);
    }
    private void appendEvent(LabSample value,SampleStatusEnum from,String reason){
        var entry=new LabSampleEvent();entry.setDepartmentId(value.getDepartmentId());entry.setSampleId(value.getId());
        entry.setFromStatus(from==null?null:from.getCode());entry.setToStatus(value.getStatus().getCode());
        entry.setEncryptedReason(reason==null || reason.isBlank()?null:records.encrypt(java.util.Map.of("sampleId",value.getId(),"reason",reason.trim())));
        entry.setActorId(InstitutionContext.requireUserActor());entry.setOccurredAt(LocalDateTime.now());events.insert(entry);
    }
    private LambdaQueryWrapper<LabSample> authorizedQuery(){
        var scope=ClinicalAccess.requireClinical();
        return new LambdaQueryWrapper<LabSample>().eq(LabSample::getInstitutionId,scope.institutionId()).eq(LabSample::getDepartmentId,scope.departmentId())
                .exists("SELECT 1 FROM lab_patient_department g WHERE g.patient_id=lab_sample.patient_id AND g.institution_id="+scope.institutionId()+" AND g.department_id="+scope.departmentId()+" AND g.active=1");
    }
    private LabSample owned(Long id,boolean lock){
        var query=authorizedQuery().eq(LabSample::getId,id);if(lock)query.last("FOR UPDATE");
        var value=samples.selectOne(query);if(value==null)throw new BizException(40400,"样本不存在或无权访问");return value;
    }
    private StoredSample payload(LabSample value){
        var payload=records.decrypt(value.getEncryptedDetails(),StoredSample.class);
        if(!Objects.equals(payload.patientId(),value.getPatientId()) || !Objects.equals(payload.departmentId(),value.getDepartmentId())
                || payload.number()==null || !Objects.equals(records.index("sample-number",payload.number()),value.getNumberIndex()))
            throw new BizException(40900,"样本关联完整性校验失败");
        return payload;
    }
    private SampleDetailVO vo(LabSample value){var p=payload(value);return new SampleDetailVO(value.getId(),value.getPatientId(),p.number(),p.specimenType(),p.collectedAt(),value.getStatus().getCode(),value.getVersion(),value.getReceivedAt());}
}
