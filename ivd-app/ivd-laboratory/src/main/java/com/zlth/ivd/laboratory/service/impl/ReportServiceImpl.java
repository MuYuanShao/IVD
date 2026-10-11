package com.zlth.ivd.laboratory.service.impl;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.vo.*;
import com.zlth.ivd.laboratory.service.ReportService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.enums.ReportStatusEnum;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class ReportServiceImpl implements ReportService {
    private final LabReportMapper mapper;
    private final LabReportEventMapper events;
    private final LabReportResultMapper references;
    private final LabResultService results;
    private final ResultEvidenceService evidence;
    private final SampleService samples;
    private final PatientService patients;
    private final ClinicalRecordSupport records;
    private final com.zlth.ivd.laboratory.pdf.ReportPdfRenderer renderer;
    private final com.zlth.ivd.laboratory.storage.ReportObjectStore objects;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    public ReportDetailVO create(ReportRequest request){
        var scope=ClinicalAccess.requireClinical();records.validate(request);
        if(request.version()!=null)throw new BizException(40001,"新报告不接受旧版本号");
        var snapshot=capture(request,1);
        var value=new LabReport();value.setDepartmentId(scope.departmentId());value.setSampleId(request.sampleId());value.setPatientId(snapshot.patient().id());value.setEdition(1);
        value.setNumberIndex(records.index("report-number",request.number()));value.setStatus(ReportStatusEnum.DRAFT);value.setVersion(0);value.setCreatedBy(scope.actorId());value.setCreatedAt(LocalDateTime.now());value.setUpdatedAt(value.getCreatedAt());value.setEncryptedSnapshot(records.encrypt(snapshot));
        if(value.getEncryptedSnapshot().length()>12*1024*1024)throw new BizException(40001,"报告快照超过安全大小限制");
        try{mapper.insert(value);}catch(org.springframework.dao.DuplicateKeyException error){throw new BizException(40900,"报告编号已存在，请使用更正流程");}
        replaceReferences(value,snapshot);append(value,null,"CREATE");records.audit("laboratory-report","CREATE",value.getId());return vo(value,snapshot);
    }
    public ReportDetailVO update(Long id,ReportRequest request){
        records.validate(request);var value=locked(id);editable(value);version(value,request.version());
        var prior=read(value);
        if(!Objects.equals(prior.number(),request.number().trim()) || !Objects.equals(value.getSampleId(),request.sampleId()))throw new BizException(40900,"不能更换报告编号或患者样本");
        var snapshot=capture(request,value.getEdition());snapshot=new ReportSnapshot(snapshot.number(),snapshot.sampleId(),snapshot.edition(),snapshot.patient(),snapshot.lines(),prior.stamps(),prior.workflowNote());
        snapshot=stamp(snapshot,"EDIT",null);value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);replaceReferences(value,snapshot);append(value,value.getStatus(),"EDIT");records.audit("laboratory-report","EDIT",id);return vo(value,snapshot);
    }
    public ReportDetailVO detail(Long id){var value=owned(id,false);samples.detail(value.getSampleId());var snapshot=read(value);records.audit("laboratory-report","DETAIL",id);return vo(value,snapshot);}
    public PageVO<ReportSummaryVO> list(int page,int size,Long sampleId){
        var query=query();if(sampleId!=null){samples.detail(sampleId);query.eq(LabReport::getSampleId,sampleId);}
        var scope=ClinicalAccess.requireClinical();query.exists("SELECT 1 FROM lab_patient_department g WHERE g.patient_id=lab_report.patient_id AND g.institution_id="+scope.institutionId()+" AND g.department_id="+scope.departmentId()+" AND g.active=1");
        var result=mapper.selectPage(new Page<LabReport>(Math.max(1,page),Math.min(100,Math.max(1,size))),query.orderByDesc(LabReport::getId));
        var rows=result.getRecords().stream().map(this::summary).toList();records.audit("laboratory-report","LIST",null);return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),rows);
    }
    public ReportDetailVO submit(Long id,ReportActionRequest request){
        records.validate(request);var value=locked(id);version(value,request.version());var from=value.getStatus();from.validateTransition(ReportStatusEnum.PENDING_REVIEW);var prior=read(value);
        var refreshed=capture(new ReportRequest(prior.number(),prior.sampleId(),prior.lines().stream().map(line->line.context().result().id()).toList(),null),value.getEdition());
        refreshed=new ReportSnapshot(refreshed.number(),refreshed.sampleId(),refreshed.edition(),refreshed.patient(),refreshed.lines(),prior.stamps(),prior.workflowNote());
        var snapshot=stamp(refreshed,"SUBMIT",request.reason());value.setStatus(ReportStatusEnum.PENDING_REVIEW);value.setReviewedBy(null);value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);replaceReferences(value,snapshot);append(value,from,"SUBMIT");records.audit("laboratory-report","SUBMIT",id);return vo(value,snapshot);
    }
    public ReportDetailVO review(Long id,ReportActionRequest request){
        records.validate(request);var value=locked(id);version(value,request.version());var from=value.getStatus();from.validateTransition(ReportStatusEnum.REVIEWED);var prior=read(value);separateActor(value,prior);
        verifyCurrent(prior);
        boolean attention=prior.lines().stream().anyMatch(line->!"NORMAL".equals(line.context().result().referenceStatus()) || !java.util.Set.of("NORMAL","NOT_APPLICABLE").contains(line.context().result().measurementStatus()) || line.evidence().isEmpty() || line.evidence().stream().anyMatch(item->item.consumption()==null));
        if(attention && (!Boolean.TRUE.equals(request.acknowledgeRisks()) || request.reason()==null || request.reason().isBlank()))throw new BizException(40001,"存在异常、缺失或证据不完整，必须由复核人确认风险并填写说明");
        var snapshot=stamp(prior,"REVIEW",request.reason());value.setStatus(ReportStatusEnum.REVIEWED);value.setReviewedBy(InstitutionContext.requireUserActor());value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);append(value,from,"REVIEW");records.audit("laboratory-report","REVIEW",id);return vo(value,snapshot);
    }
    public ReportDetailVO returnForEdit(Long id,ReportActionRequest request){
        records.validate(request);reason(request);var value=locked(id);version(value,request.version());var from=value.getStatus();from.validateTransition(ReportStatusEnum.RETURNED);var prior=read(value);separateActor(value,prior);
        var snapshot=stamp(prior,"RETURN",request.reason());value.setStatus(ReportStatusEnum.RETURNED);value.setReviewedBy(null);value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);append(value,from,"RETURN");records.audit("laboratory-report","RETURN",id);return vo(value,snapshot);
    }
    public ReportDetailVO sign(Long id,ReportActionRequest request){
        ClinicalAccess.requireSigner();records.validate(request);var value=locked(id);
        if(value.getStatus()==ReportStatusEnum.SIGNED){
            if(!Objects.equals(value.getSignedBy(),InstitutionContext.requireUserActor()) || (!Objects.equals(request.version(),value.getVersion()) && !Objects.equals(request.version(),value.getVersion()-1)))throw new BizException(40900,"报告已签发，不能再次签发");
            records.audit("laboratory-report","SIGN_REPLAY",id);return vo(value,read(value));
        }
        version(value,request.version());var from=value.getStatus();from.validateTransition(ReportStatusEnum.SIGNED);var prior=read(value);separateActor(value,prior);verifyCurrent(prior);
        if(value.getReviewedBy()==null)throw new BizException(40900,"报告没有人工复核证据");
        var snapshot=stamp(prior,"SIGN",request.reason());byte[] pdf=renderer.render(snapshot,"SIGNED");String digest=digest(pdf);
        String key=InstitutionContext.require().institutionId()+"/lab-reports/"+id+"/"+UUID.randomUUID().toString().replace("-","")+".enc";
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED){try{objects.remove(key);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(ReportServiceImpl.class).error("报告回滚对象清理失败，需核对技术对象 ID；报告 ID={}",id);}}}
        });
        objects.put(key,records.encryptBytes(pdf));
        value.setStatus(ReportStatusEnum.SIGNED);value.setSignedBy(InstitutionContext.requireUserActor());value.setSignedAt(snapshot.stamps().get(snapshot.stamps().size()-1).at());value.setPdfKey(key);value.setPdfDigest(digest);value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);append(value,from,"SIGN");
        if(value.getRootId()!=null){
            var older=mapper.selectList(family(value.getRootId()).lt(LabReport::getEdition,value.getEdition()).eq(LabReport::getStatus,ReportStatusEnum.SIGNED).last("FOR UPDATE"));
            for(var previous:older){previous.getStatus().validateTransition(ReportStatusEnum.SUPERSEDED);previous.setStatus(ReportStatusEnum.SUPERSEDED);persist(previous);append(previous,ReportStatusEnum.SIGNED,"SUPERSEDE");records.audit("laboratory-report","SUPERSEDE",previous.getId());}
        }
        records.audit("laboratory-report","SIGN",id);return vo(value,snapshot);
    }
    public ReportDetailVO correct(Long id,ReportActionRequest request){
        records.validate(request);reason(request);var original=locked(id);version(original,request.version());
        if(original.getStatus()!=ReportStatusEnum.SIGNED)throw new BizException(40900,"仅已签发的当前报告可发起更正");
        long root=original.getRootId()==null?original.getId():original.getRootId();var editions=mapper.selectList(family(root).orderByDesc(LabReport::getEdition));
        if(!Objects.equals(editions.get(0).getId(),id))throw new BizException(40900,"已有更新版本或更正草稿，请先处理该版本");
        var prior=read(original);int next=original.getEdition()+1;var snapshot=capture(new ReportRequest(prior.number(),prior.sampleId(),prior.lines().stream().map(line->line.context().result().id()).toList(),null),next);snapshot=stamp(snapshot,"CORRECT",request.reason());
        var value=new LabReport();value.setDepartmentId(original.getDepartmentId());value.setSampleId(original.getSampleId());value.setPatientId(original.getPatientId());value.setRootId(root);value.setEdition(next);value.setNumberIndex(original.getNumberIndex());value.setStatus(ReportStatusEnum.DRAFT);value.setVersion(0);value.setCreatedBy(InstitutionContext.requireUserActor());value.setCreatedAt(LocalDateTime.now());value.setUpdatedAt(value.getCreatedAt());value.setEncryptedSnapshot(records.encrypt(snapshot));mapper.insert(value);
        replaceReferences(value,snapshot);append(value,null,"CORRECT");records.audit("laboratory-report","CORRECT",value.getId());return vo(value,snapshot);
    }
    public ReportDetailVO revoke(Long id,ReportActionRequest request){
        ClinicalAccess.requireSigner();records.validate(request);reason(request);var value=locked(id);version(value,request.version());var from=value.getStatus();from.validateTransition(ReportStatusEnum.REVOKED);
        var snapshot=stamp(read(value),"REVOKE",request.reason());value.setStatus(ReportStatusEnum.REVOKED);value.setEncryptedSnapshot(records.encrypt(snapshot));persist(value);append(value,from,"REVOKE");records.audit("laboratory-report","REVOKE",id);return vo(value,snapshot);
    }
    public List<ReportSummaryVO> versions(Long id){var value=owned(id,false);samples.detail(value.getSampleId());var output=mapper.selectList(family(value.getRootId()==null?id:value.getRootId()).orderByAsc(LabReport::getEdition)).stream().map(this::summary).toList();records.audit("laboratory-report","VERSIONS",id);return output;}
    public List<ReportHistoryVO> history(Long id){var value=owned(id,false);samples.detail(value.getSampleId());var scope=ClinicalAccess.requireClinical();var output=events.selectList(new LambdaQueryWrapper<LabReportEvent>().eq(LabReportEvent::getInstitutionId,scope.institutionId()).eq(LabReportEvent::getDepartmentId,scope.departmentId()).eq(LabReportEvent::getReportId,id).orderByAsc(LabReportEvent::getId)).stream().map(event->{var snapshot=records.decrypt(event.getEncryptedSnapshot(),ReportSnapshot.class);if(!Objects.equals(snapshot.sampleId(),value.getSampleId()) || snapshot.edition()!=value.getEdition())throw new BizException(40900,"报告事件快照归属校验失败");return new ReportHistoryVO(event.getAction(),event.getFromStatus(),event.getToStatus(),event.getActorId(),event.getOccurredAt(),snapshot.workflowNote());}).toList();records.audit("laboratory-report","HISTORY",id);return output;}
    public byte[] pdf(Long id,String purpose){
        if(!Set.of("PREVIEW","DOWNLOAD","PRINT").contains(purpose))throw new BizException(40001,"报告获取用途无效");
        var value=owned(id,false);samples.detail(value.getSampleId());var snapshot=read(value);byte[] pdf;
        if(value.getStatus()==ReportStatusEnum.REVOKED || value.getStatus()==ReportStatusEnum.SUPERSEDED)throw new BizException(40900,"已撤销或被更正替代的报告禁止正常业务打印下载");
        if(value.getStatus()==ReportStatusEnum.SIGNED){
            if(value.getPdfKey()==null || !value.getPdfKey().startsWith(InstitutionContext.require().institutionId()+"/lab-reports/"+id+"/"))throw new BizException(40900,"报告对象归属校验失败");
            pdf=records.decryptBytes(objects.get(value.getPdfKey()));if(!Objects.equals(digest(pdf),value.getPdfDigest()))throw new BizException(40900,"报告摘要校验失败，拒绝提供文件");
        }else{if(!"PREVIEW".equals(purpose))throw new BizException(40900,"未签发报告仅允许草稿预览");pdf=renderer.render(snapshot,value.getStatus().getCode());}
        records.audit("laboratory-report",purpose,id);return pdf;
    }
    private ReportSnapshot capture(ReportRequest request,int edition){
        var sample=samples.requireReceivedForWrite(request.sampleId());
        if(new HashSet<>(request.resultIds()).size()!=request.resultIds().size())throw new BizException(40001,"报告项目结果不能重复引用");
        var lines=new ArrayList<ReportSnapshot.Line>();
        for(Long id:request.resultIds().stream().sorted().toList()){
            var context=results.lockContext(id);
            if(!Objects.equals(context.result().sampleId(),sample.id()) || !Objects.equals(context.patient().id(),sample.patientId()))throw new BizException(40900,"报告结果不属于指定患者样本");
            lines.add(new ReportSnapshot.Line(context,evidence.list(id,context.result().version())));
        }
        return new ReportSnapshot(request.number().trim(),sample.id(),edition,patients.requireAuthorizedForWrite(sample.patientId()),List.copyOf(lines),List.of(),null);
    }
    private void replaceReferences(LabReport value,ReportSnapshot snapshot){
        references.delete(new LambdaQueryWrapper<LabReportResult>().eq(LabReportResult::getInstitutionId,InstitutionContext.require().institutionId()).eq(LabReportResult::getReportId,value.getId()));
        for(var line:snapshot.lines()){var reference=new LabReportResult();reference.setDepartmentId(value.getDepartmentId());reference.setReportId(value.getId());reference.setResultId(line.context().result().id());reference.setResultVersion(line.context().result().version());references.insert(reference);}
    }
    private void verifyCurrent(ReportSnapshot snapshot){for(var line:snapshot.lines()){var current=results.lockContext(line.context().result().id()).result();if(!Objects.equals(current.version(),line.context().result().version()))throw new BizException(40900,"结果已变更，请退回重新提交报告");}}
    private LabReport locked(Long id){var initial=owned(id,false);samples.requireReceivedForWrite(initial.getSampleId());if(initial.getRootId()!=null)owned(initial.getRootId(),true);return owned(id,true);}
    private void editable(LabReport value){if(!Set.of(ReportStatusEnum.DRAFT,ReportStatusEnum.RETURNED).contains(value.getStatus()))throw new BizException(40900,"报告不是可编辑草稿");}
    private void version(LabReport value,Integer version){if(version==null || !Objects.equals(version,value.getVersion()))throw new BizException(40900,"报告版本已变更，请刷新后重试");}
    private void reason(ReportActionRequest request){if(request.reason()==null || request.reason().isBlank())throw new BizException(40001,"该操作必须填写原因");}
    private void separateActor(LabReport value,ReportSnapshot snapshot){if(entryActors(value,snapshot).contains(InstitutionContext.requireUserActor()))throw new BizException(40300,"复核人和签发人均不能是报告、结果或证据录入人员");}
    private Set<Long> entryActors(LabReport value,ReportSnapshot snapshot){var actors=new HashSet<Long>();actors.add(value.getCreatedBy());for(var line:snapshot.lines()){actors.add(line.context().createdBy());actors.add(line.context().editedBy());for(var evidence:line.evidence())actors.add(evidence.boundBy());}for(var stamp:snapshot.stamps())if(Set.of("CREATE","EDIT","SUBMIT","CORRECT").contains(stamp.action()))actors.add(stamp.actorId());actors.remove(null);return actors;}
    private ReportSnapshot stamp(ReportSnapshot snapshot,String action,String note){var stamps=new ArrayList<>(snapshot.stamps());stamps.add(new ReportSnapshot.Stamp(action,InstitutionContext.requireUserActor(),SecurityUtils.getCurrentUsername(),LocalDateTime.now()));return new ReportSnapshot(snapshot.number(),snapshot.sampleId(),snapshot.edition(),snapshot.patient(),snapshot.lines(),List.copyOf(stamps),note==null?snapshot.workflowNote():note.trim());}
    private void persist(LabReport value){if(value.getEncryptedSnapshot().length()>12*1024*1024)throw new BizException(40001,"报告快照超过安全大小限制");value.setUpdatedAt(LocalDateTime.now());if(mapper.updateById(value)!=1)throw new BizException(40900,"报告版本已变更");}
    private LambdaQueryWrapper<LabReport> family(Long root){return query().and(q->q.eq(LabReport::getId,root).or().eq(LabReport::getRootId,root));}
    private String digest(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private LambdaQueryWrapper<LabReport> query(){var scope=ClinicalAccess.requireClinical();return new LambdaQueryWrapper<LabReport>().eq(LabReport::getInstitutionId,scope.institutionId()).eq(LabReport::getDepartmentId,scope.departmentId());}
    private LabReport owned(Long id,boolean lock){var q=query().eq(LabReport::getId,id);if(lock)q.last("FOR UPDATE");var value=mapper.selectOne(q);if(value==null)throw new BizException(40400,"报告不存在或无权访问");return value;}
    private ReportSnapshot read(LabReport value){
        var s=records.decrypt(value.getEncryptedSnapshot(),ReportSnapshot.class);
        if(!Objects.equals(s.sampleId(),value.getSampleId()) || !Objects.equals(s.patient().id(),value.getPatientId()) || s.edition()!=value.getEdition()
                || !Objects.equals(records.index("report-number",s.number()),value.getNumberIndex()))throw new BizException(40900,"报告快照关联校验失败");return s;
    }
    private void append(LabReport value,ReportStatusEnum from,String action){
        var event=new LabReportEvent();event.setDepartmentId(value.getDepartmentId());event.setReportId(value.getId());event.setAction(action);event.setFromStatus(from==null?null:from.getCode());event.setToStatus(value.getStatus().getCode());event.setEncryptedSnapshot(value.getEncryptedSnapshot());event.setActorId(InstitutionContext.requireUserActor());event.setOccurredAt(LocalDateTime.now());events.insert(event);
        publisher.publishEvent(new com.zlth.ivd.laboratory.event.ReportStatusChanged(InstitutionContext.require().institutionId(),value.getDepartmentId(),value.getId(),from,value.getStatus(),InstitutionContext.requireUserActor()));
    }
    private ReportDetailVO vo(LabReport value,ReportSnapshot s){return new ReportDetailVO(value.getId(),s.number(),value.getEdition(),value.getStatus().getCode(),value.getVersion(),value.getSampleId(),s.patient(),s.lines().stream().map(line->line.context().result()).toList(),value.getCreatedBy(),value.getReviewedBy(),value.getSignedBy(),value.getSignedAt(),value.getPdfDigest(),"NOT_CONFIGURED",entryActors(value,s).stream().sorted().toList());}
    private ReportSummaryVO summary(LabReport value){var s=read(value);String masked=s.number().length()>8?"****"+s.number().substring(s.number().length()-4):"****";return new ReportSummaryVO(value.getId(),masked,value.getEdition(),value.getStatus().getCode(),value.getVersion(),value.getSampleId());}
}
