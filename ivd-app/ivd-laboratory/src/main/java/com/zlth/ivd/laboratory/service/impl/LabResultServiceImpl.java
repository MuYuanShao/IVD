package com.zlth.ivd.laboratory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.service.DeviceDirectoryService;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.enums.*;
import com.zlth.ivd.laboratory.event.ResultRecorded;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.*;
import com.zlth.ivd.laboratory.vo.*;
import com.zlth.ivd.system.service.InstitutionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draft clinical evidence only. No signing, stock deduction, billing or automatic diagnosis. */
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class LabResultServiceImpl implements LabResultService {
    private final LabResultMapper results;
    private final LabResultRevisionMapper revisions;
    private final SampleService samples;
    private final PatientService patients;
    private final LabCatalogService catalog;
    private final DeviceDirectoryService devices;
    private final InstitutionService institutions;
    private final ClinicalRecordSupport records;
    private final ApplicationEventPublisher publisher;
    private final DeviceCapabilityService capabilities;
    private final LabReportResultMapper reportReferences;

    public record TestingSnapshot(Long id,String code,String name,String type) { }
    public record EvaluationSnapshot(String status,String reason,IntervalVO interval) { }
    /** Everything needed to reconstruct the recorded result is encrypted, including patient context. */
    public record StoredResult(Long sampleId,Long departmentId,Long itemId,Long deviceId,Long testingInstitutionId,
            String executionMode,int version,PatientDetailVO patient,SampleDetailVO sample,TestItemVO item,
            DeviceDirectoryService.DeviceReference device,TestingSnapshot testingInstitution,
            String result,String unit,LocalDateTime testedAt,EvaluationSnapshot reference,EvaluationSnapshot measurement) { }

    @Override public ResultDetailVO create(Long sampleId,ResultEntryRequest request){
        var scope=ClinicalAccess.requireClinical();records.validate(request);
        if(request.version()!=null)throw new BizException(40001,"新增结果不接受修订版本");
        var sample=samples.requireReceivedForWrite(sampleId);
        var value=new LabResult();value.setSampleId(sampleId);value.setDepartmentId(scope.departmentId());value.setItemId(request.itemId());
        value.setVersion(0);value.setCreatedBy(scope.actorId());value.setUpdatedBy(scope.actorId());
        value.setCreatedAt(LocalDateTime.now());value.setUpdatedAt(value.getCreatedAt());
        var snapshot=build(value,sample,request,0);value.setEncryptedSnapshot(records.encrypt(snapshot));
        try{results.insert(value);}catch(DuplicateKeyException error){throw new BizException(40900,"该样本项目已有结果，请使用版本修订，不能重复覆盖");}
        appendRevision(value);records.audit("laboratory-result","CREATE",value.getId());publish(value);
        return vo(value.getId(),snapshot);
    }
    @Override @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public ResultDetailVO update(Long id,ResultEntryRequest request){
        records.validate(request);var initial=owned(id,false);
        var sample=samples.requireReceivedForWrite(initial.getSampleId());var value=owned(id,true);
        requireEditable(id);
        if(!Objects.equals(request.itemId(),value.getItemId()))throw new BizException(40900,"不能修改结果所属项目");
        if(request.version()==null || !Objects.equals(request.version(),value.getVersion()))throw new BizException(40900,"结果版本已变更，请刷新后重试");
        verify(value);
        var snapshot=build(value,sample,request,value.getVersion()+1);value.setEncryptedSnapshot(records.encrypt(snapshot));
        value.setUpdatedBy(InstitutionContext.requireUserActor());value.setUpdatedAt(LocalDateTime.now());
        if(results.updateById(value)!=1)throw new BizException(40900,"结果版本已变更，请刷新后重试");
        appendRevision(value);records.audit("laboratory-result","UPDATE",id);publish(value);return vo(id,snapshot);
    }
    @Override public ResultDetailVO detail(Long id){
        var value=owned(id,false);samples.detail(value.getSampleId());var snapshot=verify(value);
        records.audit("laboratory-result","DETAIL",id);return vo(id,snapshot);
    }
    @Override public PageVO<ResultDetailVO> list(Long sampleId,int page,int size){
        samples.detail(sampleId);
        var result=results.selectPage(new Page<LabResult>(Math.max(1,page),Math.min(100,Math.max(1,size))),query().eq(LabResult::getSampleId,sampleId).orderByDesc(LabResult::getId));
        var rows=result.getRecords().stream().map(value->vo(value.getId(),verify(value))).toList();
        records.audit("laboratory-result","LIST",sampleId);return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),rows);
    }
    @Override public List<ResultDetailVO> versions(Long id){
        var value=owned(id,false);samples.detail(value.getSampleId());verify(value);
        var scope=ClinicalAccess.requireClinical();
        var history=revisions.selectList(new LambdaQueryWrapper<LabResultRevision>().eq(LabResultRevision::getInstitutionId,scope.institutionId())
                .eq(LabResultRevision::getDepartmentId,scope.departmentId()).eq(LabResultRevision::getResultId,id).orderByAsc(LabResultRevision::getVersion));
        var output=history.stream().map(revision->{
            var snapshot=records.decrypt(revision.getEncryptedSnapshot(),StoredResult.class);
            if(snapshot.version()!=revision.getVersion() || !Objects.equals(snapshot.sampleId(),value.getSampleId()) || !Objects.equals(snapshot.itemId(),value.getItemId())
                    || !Objects.equals(snapshot.departmentId(),scope.departmentId()))throw new BizException(40900,"结果历史关联完整性校验失败");
            return vo(id,snapshot);
        }).toList();records.audit("laboratory-result","VERSIONS",id);return output;
    }
    @Override public List<TestingInstitutionOptionVO> testingInstitutions(){
        var scope=ClinicalAccess.requireClinical();
        return institutions.options().stream().filter(option->Objects.equals(option.id(),scope.institutionId())
                || ("EXTERNAL".equals(option.type()) && Objects.equals(option.ownerInstitutionId(),scope.institutionId())))
                .map(option->new TestingInstitutionOptionVO(option.id(),option.code(),option.name(),option.type())).toList();
    }
    @Override public PageVO<ResultDetailVO> search(Long deviceId,Long patientId,int page,int size){
        var scope=ClinicalAccess.requireClinical();
        if(deviceId!=null)devices.requireOwned(deviceId);
        if(patientId!=null)patients.detail(patientId);
        var q=query().exists("SELECT 1 FROM lab_sample s JOIN lab_patient_department g ON g.patient_id=s.patient_id AND g.institution_id=s.institution_id"
                +" WHERE s.id=lab_result.sample_id AND s.institution_id="+scope.institutionId()+" AND s.department_id="+scope.departmentId()
                +" AND g.department_id="+scope.departmentId()+" AND g.active=1"+(patientId==null?"":" AND s.patient_id="+patientId));
        if(deviceId!=null)q.eq(LabResult::getDeviceId,deviceId);
        var result=results.selectPage(new Page<LabResult>(Math.max(1,page),Math.min(100,Math.max(1,size))),q.orderByDesc(LabResult::getId));
        var rows=result.getRecords().stream().map(value->vo(value.getId(),verify(value))).toList();
        records.audit("laboratory-result","SEARCH",null);return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),rows);
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY,rollbackFor=Exception.class)
    public ResultContextVO lockContext(Long id){
        var initial=owned(id,false);samples.requireReceivedForWrite(initial.getSampleId());var value=owned(id,true);var s=verify(value);
        records.audit("laboratory-result","CONTEXT",id);return new ResultContextVO(vo(id,s),s.patient(),s.sample(),s.item(),value.getCreatedBy(),value.getUpdatedBy());
    }
    @Override public void requireEditable(Long id){owned(id,false);if(reportReferences.frozenReferences(id,InstitutionContext.require().institutionId())>0)throw new BizException(40900,"结果正在报告复核或签发流程中，请先退回报告再编辑或追加证据");}
    private StoredResult build(LabResult value,SampleDetailVO sample,ResultEntryRequest request,int version){
        var scope=ClinicalAccess.requireClinical();var patient=patients.requireAuthorizedForWrite(sample.patientId());
        var item=catalog.lockItem(request.itemId(),true);
        if(request.testedAt().isBefore(sample.collectedAt()))throw new BizException(40001,"检测时间不能早于采集时间");
        if("NUMERIC".equals(item.resultType())){
            try{new BigDecimal(request.result().trim());}catch(NumberFormatException error){throw new BizException(40001,"定量结果必须为有效数值");}
        }
        var mode=ExecutionModeEnum.fromCode(request.executionMode());
        if(Boolean.TRUE.equals(item.deviceRequired()) && mode==ExecutionModeEnum.MANUAL)throw new BizException(40001,"该项目要求仪器检测，不能改为手工录入");
        if((mode==ExecutionModeEnum.INSTRUMENT)!=(request.deviceId()!=null))throw new BizException(40001,"仪器检测必须选择设备，手工或外送检测不能绑定本地设备");
        var device=request.deviceId()==null?null:devices.lockOwned(request.deviceId(),true);
        if(device!=null)capabilities.requireExecutable(device.id(),item.id());
        long testingId=request.testingInstitutionId()==null?scope.institutionId():request.testingInstitutionId();
        var testing=institutions.options().stream().filter(option->option.id().equals(testingId))
                .filter(option->Objects.equals(option.id(),scope.institutionId()) || ("EXTERNAL".equals(option.type()) && Objects.equals(option.ownerInstitutionId(),scope.institutionId())))
                .findFirst().orElseThrow(()->new BizException(40400,"检测机构不存在、已停用或无权引用"));
        if((mode==ExecutionModeEnum.EXTERNAL)!=testing.type().equals("EXTERNAL"))throw new BizException(40001,"外送检测须选择本机构维护的外送机构，本地检测须选择本院");
        value.setDeviceId(request.deviceId());value.setTestingInstitutionId(testingId);value.setExecutionMode(mode);
        var reference=evaluate(item,sample,patient,request,IntervalKindEnum.REFERENCE);
        var measurement=mode==ExecutionModeEnum.INSTRUMENT && "NUMERIC".equals(item.resultType())?evaluate(item,sample,patient,request,IntervalKindEnum.MEASUREMENT)
                :new EvaluationSnapshot("NOT_APPLICABLE","当前检测方式或结果类型不使用本地设备测量范围",null);
        return new StoredResult(sample.id(),scope.departmentId(),item.id(),request.deviceId(),testingId,mode.getCode(),version,patient,sample,item,device,
                new TestingSnapshot(testing.id(),testing.code(),testing.name(),testing.type()),request.result().trim(),request.unit().trim(),request.testedAt(),reference,measurement);
    }
    private EvaluationSnapshot evaluate(TestItemVO item,SampleDetailVO sample,PatientDetailVO patient,ResultEntryRequest request,IntervalKindEnum kind){
        if(patient.birthDate()==null || patient.birthDate().isAfter(sample.collectedAt().toLocalDate()))return new EvaluationSnapshot("MISSING","患者出生日期缺失或与采集日期不一致，需人工处理",null);
        long days=ChronoUnit.DAYS.between(patient.birthDate(),sample.collectedAt().toLocalDate());
        if(days>73000)return new EvaluationSnapshot("MISSING","患者年龄超出区间配置范围，需人工处理",null);
        var evaluation=catalog.evaluate(new IntervalEvaluationRequest(item.id(),request.deviceId(),item.methodology(),request.unit().trim(),patient.sex(),(int)days,sample.collectedAt().toLocalDate(),request.result().trim(),kind.getCode()));
        return new EvaluationSnapshot(evaluation.status(),evaluation.reason(),evaluation.intervalId()==null?null:catalog.interval(evaluation.intervalId()));
    }
    private LambdaQueryWrapper<LabResult> query(){var scope=ClinicalAccess.requireClinical();return new LambdaQueryWrapper<LabResult>().eq(LabResult::getInstitutionId,scope.institutionId()).eq(LabResult::getDepartmentId,scope.departmentId());}
    private LabResult owned(Long id,boolean lock){var q=query().eq(LabResult::getId,id);if(lock)q.last("FOR UPDATE");var value=results.selectOne(q);if(value==null)throw new BizException(40400,"结果不存在或无权访问");return value;}
    private StoredResult verify(LabResult value){
        var s=records.decrypt(value.getEncryptedSnapshot(),StoredResult.class);
        if(!Objects.equals(s.sampleId(),value.getSampleId()) || !Objects.equals(s.itemId(),value.getItemId()) || !Objects.equals(s.deviceId(),value.getDeviceId())
                || !Objects.equals(s.departmentId(),value.getDepartmentId()) || !Objects.equals(s.testingInstitutionId(),value.getTestingInstitutionId())
                || s.version()!=value.getVersion() || !Objects.equals(s.executionMode(),value.getExecutionMode().getCode()))throw new BizException(40900,"结果关联完整性校验失败");
        return s;
    }
    private void appendRevision(LabResult value){var revision=new LabResultRevision();revision.setDepartmentId(value.getDepartmentId());revision.setResultId(value.getId());revision.setVersion(value.getVersion());revision.setEncryptedSnapshot(value.getEncryptedSnapshot());revision.setActorId(InstitutionContext.requireUserActor());revision.setOccurredAt(LocalDateTime.now());revisions.insert(revision);}
    private void publish(LabResult value){publisher.publishEvent(new ResultRecorded(InstitutionContext.require().institutionId(),value.getDepartmentId(),value.getSampleId(),value.getId(),value.getVersion()));}
    private ResultDetailVO vo(Long id,StoredResult s){return new ResultDetailVO(id,s.sampleId(),s.patient().id(),s.itemId(),s.deviceId(),s.testingInstitutionId(),s.executionMode(),s.result(),s.unit(),s.testedAt(),s.version(),s.item().code(),s.item().name(),s.device()==null?null:s.device().name(),s.testingInstitution().name(),s.reference().status(),s.reference().reason(),s.measurement().status(),s.measurement().reason(),s.reference().interval(),s.measurement().interval());}
}
