package com.zlth.ivd.laboratory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.audit.*;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.laboratory.dto.PatientRequest;
import com.zlth.ivd.laboratory.dto.PatientDepartmentRequest;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.PatientService;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class PatientServiceImpl implements PatientService {
    private final LabPatientMapper patients;
    private final LabPatientDepartmentMapper grants;
    private final ClinicalCrypto crypto;
    private final ObjectMapper json;
    private final Validator validator;
    private final OperationAuditLogWriter audits;
    private final AuthenticationDirectoryService directory;

    /** Only the encrypted JSON contains demographic fields, including the patient number. */
    public record StoredProfile(String number,String name,String sex,LocalDate birthDate) { }

    @Override public PatientDetailVO create(PatientRequest request) {
        var scope=ClinicalAccess.requireClinical();validate(request);
        var profile=new StoredProfile(request.number().trim(),request.name().trim(),request.sex(),request.birthDate());
        var patient=new LabPatient();patient.setNumberIndex(crypto.index("patient-number",profile.number()));
        patient.setEncryptedProfile(encrypt(profile));patient.setVersion(0);
        patient.setCreatedAt(LocalDateTime.now());patient.setUpdatedAt(patient.getCreatedAt());
        try { patients.insert(patient); }
        catch(DuplicateKeyException error) { throw new BizException(40900,"资料无法创建，请由授权管理员核对；不会自动合并患者"); }
        var grant=new LabPatientDepartment();grant.setPatientId(patient.getId());grant.setDepartmentId(scope.departmentId());
        grant.setActive(true);grant.setCreatedAt(patient.getCreatedAt());grant.setUpdatedAt(patient.getCreatedAt());grants.insert(grant);
        audit("CREATE",patient.getId());return detailVO(patient,profile);
    }
    @Override public PatientDetailVO detail(Long id) {
        var patient=patients.selectOne(authorizedQuery().eq(LabPatient::getId,id));
        if(patient==null) throw new BizException(40400,"患者不存在或无权访问");
        var profile=decrypt(patient);audit("DETAIL",id);return detailVO(patient,profile);
    }
    @Override public PatientDetailVO update(Long id,PatientRequest request) {
        var scope=ClinicalAccess.requireClinical();validate(request);
        if(grants.lockActive(id,scope.institutionId(),scope.departmentId())==null) throw new BizException(40400,"患者不存在或无权访问");
        var patient=patients.selectOne(authorizedQuery().eq(LabPatient::getId,id));
        if(patient==null) throw new BizException(40400,"患者不存在或无权访问");
        if(request.version()==null || !request.version().equals(patient.getVersion())) throw new BizException(40900,"资料版本已变更，请刷新后重试");
        var profile=new StoredProfile(request.number().trim(),request.name().trim(),request.sex(),request.birthDate());
        patient.setNumberIndex(crypto.index("patient-number",profile.number()));patient.setEncryptedProfile(encrypt(profile));patient.setUpdatedAt(LocalDateTime.now());
        try {
            if(patients.update(patient,authorizedQuery().eq(LabPatient::getId,id))!=1) throw new BizException(40900,"资料版本已变更，请刷新后重试");
        } catch(DuplicateKeyException error) { throw new BizException(40900,"资料无法更新，请由授权管理员核对；不会自动合并患者"); }
        audit("UPDATE",id);return detailVO(patient,profile);
    }
    @Override public PageVO<PatientSummaryVO> list(int page,int size) {
        var result=patients.selectPage(new Page<LabPatient>(Math.max(1,page),Math.min(100,Math.max(1,size))),
                authorizedQuery().orderByDesc(LabPatient::getId));
        var summaries=result.getRecords().stream().map(patient->{
            var profile=decrypt(patient);
            String name=profile.name().codePointCount(0,profile.name().length())>1?profile.name().substring(0,profile.name().offsetByCodePoints(0,1))+"*":"*";
            // Short identifiers are completely masked; never expose all of a short patient number.
            String number=profile.number().length()>8?"****"+profile.number().substring(profile.number().length()-4):"****";
            return new PatientSummaryVO(patient.getId(),number,name,patient.getVersion());
        }).toList();
        audit("LIST",null);return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),summaries);
    }
    private LambdaQueryWrapper<LabPatient> authorizedQuery() {
        var scope=ClinicalAccess.requireClinical();
        return new LambdaQueryWrapper<LabPatient>().eq(LabPatient::getInstitutionId,scope.institutionId())
                .exists("SELECT 1 FROM lab_patient_department g WHERE g.patient_id=lab_patient.id AND g.institution_id="
                        +scope.institutionId()+" AND g.department_id="+scope.departmentId()+" AND g.active=1");
    }
    @Override public void setDepartmentAccess(Long id,PatientDepartmentRequest request) {
        var scope=InstitutionContext.require();
        if(!scope.role().equals("SYSTEM_ADMIN") || scope.actorId()==null) throw new BizException(40300,"仅系统管理员可显式调整患者科室授权");
        if(request==null || !validator.validate(request).isEmpty()) throw new BizException(40001,"科室授权参数无效");
        if(request.active()==request.purpose().equals("REVOCATION")) throw new BizException(40001,"授权状态与用途不一致");
        var patient=patients.selectOne(new LambdaQueryWrapper<LabPatient>().select(LabPatient::getId).eq(LabPatient::getInstitutionId,scope.institutionId()).eq(LabPatient::getId,id));
        if(patient==null) throw new BizException(40400,"患者不存在或无权访问");
        var department=directory.activeDepartment(request.departmentId());
        if(department==null || !(department.get("institution_id") instanceof Number owner) || owner.longValue()!=scope.institutionId())
            throw new BizException(40400,"科室不存在或无权访问");
        var query=new LambdaQueryWrapper<LabPatientDepartment>().eq(LabPatientDepartment::getInstitutionId,scope.institutionId())
                .eq(LabPatientDepartment::getPatientId,id).eq(LabPatientDepartment::getDepartmentId,request.departmentId()).last("FOR UPDATE");
        var grant=grants.selectOne(query);
        if(grant==null && request.active()) {
            grant=new LabPatientDepartment();grant.setPatientId(id);grant.setDepartmentId(request.departmentId());grant.setActive(true);
            grant.setCreatedAt(LocalDateTime.now());grant.setUpdatedAt(grant.getCreatedAt());grants.insert(grant);
        } else if(grant!=null) {
            grant.setActive(request.active());grant.setUpdatedAt(LocalDateTime.now());grants.updateById(grant);
        }
        audit("DEPARTMENT_"+request.purpose()+"_"+request.departmentId(),id);
    }
    private void validate(PatientRequest request) {
        if(request==null || !validator.validate(request).isEmpty()) throw new BizException(40001,"患者资料字段校验失败");
    }
    private String encrypt(StoredProfile profile) {
        try { return crypto.encryptText(json.writeValueAsString(profile)); }
        catch(JsonProcessingException error) { throw new BizException(50000,"临床资料序列化失败"); }
    }
    private StoredProfile decrypt(LabPatient patient) {
        try {
            var profile=json.readValue(crypto.decryptText(patient.getEncryptedProfile()),StoredProfile.class);
            if(profile.number()==null || profile.name()==null || profile.name().isBlank() || patient.getNumberIndex()==null
                    || !java.security.MessageDigest.isEqual(
                            crypto.index("patient-number",profile.number()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                            patient.getNumberIndex().getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new BizException(40900,"临床资料索引完整性校验失败");
            return profile;
        }
        catch(JsonProcessingException error) { throw new BizException(40900,"临床资料格式校验失败"); }
    }
    private PatientDetailVO detailVO(LabPatient patient,StoredProfile profile) {
        return new PatientDetailVO(patient.getId(),profile.number(),profile.name(),profile.sex(),profile.birthDate(),patient.getVersion());
    }
    private void audit(String action,Long id) {
        if(!crypto.available()) throw new BizException(50000,"临床加密密钥未配置");
        audits.append(OperationAuditEntry.builder().operatorId(InstitutionContext.requireUserActor())
                .operatorName(SecurityUtils.getCurrentUsername()).module("laboratory-patient").action(action)
                .handler("PatientService").reason(id==null?"Authorized patient list":"Patient ID="+id)
                .success(true).durationMs(0L).occurredAt(LocalDateTime.now()).build());
    }
}
