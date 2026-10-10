package com.zlth.ivd.laboratory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.service.DeviceDirectoryService;
import com.zlth.ivd.laboratory.dto.*;
import com.zlth.ivd.laboratory.entity.*;
import com.zlth.ivd.laboratory.mapper.*;
import com.zlth.ivd.laboratory.security.ClinicalAccess;
import com.zlth.ivd.laboratory.service.*;
import com.zlth.ivd.laboratory.vo.*;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class LabCatalogServiceImpl implements LabCatalogService {
    private final LabTestItemMapper items;
    private final LabIntervalMapper intervals;
    private final DeviceDirectoryService devices;
    private final IntervalPolicy policy;
    private final Validator validator;

    @Override public PageVO<TestItemVO> items(int page,int size,String keyword) {
        requireReader(); var query=itemQuery();
        if(keyword!=null && !keyword.isBlank()) query.and(q->q.like(LabTestItem::getCode,keyword.trim()).or().like(LabTestItem::getName,keyword.trim()));
        var result=items.selectPage(new Page<LabTestItem>(Math.max(1,page),Math.min(200,Math.max(1,size))),query.orderByDesc(LabTestItem::getId));
        return PageVO.of(result,this::vo);
    }
    @Override public TestItemVO item(Long id) { requireReader(); return vo(ownedItem(id,false)); }

    @Override public TestItemVO createItem(TestItemRequest request) {
        ClinicalAccess.requireMasterWriter(); validate(request); validateItem(request);
        var value=new LabTestItem(); copyItem(value,request); value.setVersion(0); value.setCreatedAt(LocalDateTime.now());
        try { items.insert(value); } catch(DuplicateKeyException error) { throw new BizException(40900,"检验项目编码已存在"); }
        return vo(value);
    }
    @Override public TestItemVO updateItem(Long id,TestItemRequest request) {
        ClinicalAccess.requireMasterWriter(); validate(request); validateItem(request);
        var value=ownedItem(id,true); version(request.version(),value.getVersion());
        boolean changed=!value.getMethodology().equals(request.methodology().trim()) || !value.getUnit().equals(request.unit().trim()) || !value.getResultType().equals(request.resultType());
        if(changed && intervals.selectCount(intervalQuery().eq(LabInterval::getItemId,id))>0)
            throw new BizException(40900,"已有关联区间，方法学、单位和结果类型不可直接修改；请新增检验项目");
        copyItem(value,request);
        try { if(items.updateById(value)!=1) throw new BizException(40900,"数据已变更，请刷新后重试"); }
        catch(DuplicateKeyException error){throw new BizException(40900,"检验项目编码已存在");}
        return vo(value);
    }
    @Override public PageVO<IntervalVO> intervals(int page,int size,Long itemId,String kind) {
        requireReader(); var query=intervalQuery();
        if(itemId!=null) { ownedItem(itemId,false); query.eq(LabInterval::getItemId,itemId); }
        if(kind!=null && !kind.isBlank()) {
            if(!Set.of("REFERENCE","MEASUREMENT").contains(kind)) throw new BizException(40001,"区间类型无效");
            query.eq(LabInterval::getKind,kind);
        }
        var result=intervals.selectPage(new Page<LabInterval>(Math.max(1,page),Math.min(200,Math.max(1,size))),query.orderByDesc(LabInterval::getId));
        var ids=result.getRecords().stream().map(LabInterval::getItemId).distinct().toList();
        Map<Long,LabTestItem> parents=ids.isEmpty()?Map.of():items.selectList(itemQuery().in(LabTestItem::getId,ids)).stream()
                .collect(java.util.stream.Collectors.toMap(LabTestItem::getId,v->v));
        return PageVO.of(result,value->vo(value,parents.get(value.getItemId())));
    }
    @Override public IntervalVO interval(Long id){requireReader(); var value=ownedInterval(id); return vo(value,ownedItem(value.getItemId(),false));}
    @Override public IntervalVO createInterval(IntervalRequest request) {
        ClinicalAccess.requireMasterWriter(); validate(request);
        var parent=ownedItem(request.itemId(),true);
        var value=new LabInterval(); copyInterval(value,request,parent); value.setVersion(0); value.setCreatedAt(LocalDateTime.now());
        policy.requireNoOverlap(rule(value),rules(parent.getId())); intervals.insert(value); return vo(value,parent);
    }
    @Override public IntervalVO updateInterval(Long id,IntervalRequest request) {
        ClinicalAccess.requireMasterWriter(); validate(request);
        var initial=ownedInterval(id);
        if(!initial.getItemId().equals(request.itemId())) throw new BizException(40900,"不能变更区间所属项目");
        var parent=ownedItem(request.itemId(),true);
        var value=ownedInterval(id); version(request.version(),value.getVersion());
        copyInterval(value,request,parent); policy.requireNoOverlap(rule(value),rules(parent.getId()));
        if(intervals.updateById(value)!=1) throw new BizException(40900,"数据已变更，请刷新后重试");
        return vo(value,parent);
    }
    @Override public IntervalEvaluationVO evaluate(IntervalEvaluationRequest request) {
        ClinicalAccess.requireClinical(); validate(request); var parent=ownedItem(request.itemId(),false);
        if(!Boolean.TRUE.equals(parent.getActive())) throw new BizException(40900,"检验项目已停用");
        if(request.deviceId()!=null) devices.requireActiveOwned(request.deviceId());
        var input=new IntervalPolicy.Input(request.itemId(),request.deviceId(),request.methodology(),request.unit(),request.sex(),request.ageDays(),request.sampleDate(),request.result());
        var result=policy.evaluate(rules(parent.getId()),input,request.kind());
        return new IntervalEvaluationVO(result.status(),result.intervalId(),result.reason());
    }
    @Override public List<DeviceOptionVO> deviceOptions(String keyword) {
        requireReader(); return devices.activeOptions(keyword).stream().map(device->new DeviceOptionVO(device.id(),device.code(),device.name())).toList();
    }

    private LambdaQueryWrapper<LabTestItem> itemQuery(){return new LambdaQueryWrapper<LabTestItem>().eq(LabTestItem::getInstitutionId,InstitutionContext.require().institutionId());}
    private LambdaQueryWrapper<LabInterval> intervalQuery(){return new LambdaQueryWrapper<LabInterval>().eq(LabInterval::getInstitutionId,InstitutionContext.require().institutionId());}
    private LabTestItem ownedItem(Long id,boolean lock){
        var value=lock?items.lockOwned(id,InstitutionContext.require().institutionId()):items.selectOne(itemQuery().eq(LabTestItem::getId,id));
        if(value==null) throw new BizException(40400,"检验项目不存在或无权访问"); return value;
    }
    private LabInterval ownedInterval(Long id){var value=intervals.selectOne(intervalQuery().eq(LabInterval::getId,id)); if(value==null) throw new BizException(40400,"区间不存在或无权访问"); return value;}
    private List<IntervalPolicy.Rule> rules(Long itemId){return intervals.selectList(intervalQuery().eq(LabInterval::getItemId,itemId)).stream().map(this::rule).toList();}
    private IntervalPolicy.Rule rule(LabInterval v){return new IntervalPolicy.Rule(v.getId(),v.getItemId(),v.getDeviceId(),v.getKind(),v.getMethodology(),v.getUnit(),v.getResultType(),v.getSex(),v.getMinAgeDays(),v.getMaxAgeDays(),v.getLowerBound(),v.getUpperBound(),v.getNormalText(),v.getValidFrom(),v.getValidTo(),Boolean.TRUE.equals(v.getActive()));}
    private void validate(Object request){if(request==null || !validator.validate(request).isEmpty()) throw new BizException(40001,"检验资料字段校验失败");}
    private void version(Integer incoming,Integer current){if(incoming==null || !incoming.equals(current)) throw new BizException(40900,"数据版本已变更，请刷新后重试");}
    private void requireReader(){var scope=InstitutionContext.require(); if(scope.actorId()==null || !Set.of("SYSTEM_ADMIN","LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role())) throw new BizException(40300,"没有检验基础资料访问权限");}
    private void validateItem(TestItemRequest r){if(r.resultType().equals("NUMERIC") && r.unit().isBlank()) throw new BizException(40001,"定量项目必须指定单位");}
    private void copyItem(LabTestItem v,TestItemRequest r){v.setCode(r.code().trim());v.setName(r.name().trim());v.setMethodology(r.methodology().trim());v.setResultType(r.resultType());v.setUnit(r.unit().trim());v.setActive(r.active());v.setUpdatedAt(LocalDateTime.now());}
    private void copyInterval(LabInterval v,IntervalRequest r,LabTestItem parent){
        if(!Boolean.TRUE.equals(parent.getActive()) && r.active()) throw new BizException(40900,"停用项目不能启用区间");
        if(r.minAgeDays()>r.maxAgeDays() || r.validFrom().isAfter(r.validTo())) throw new BizException(40001,"年龄或有效期起止范围无效");
        if(r.deviceId()!=null) {
            if(r.active()) devices.requireActiveOwned(r.deviceId());
            else devices.requireOwned(r.deviceId());
        }
        if(r.kind().equals("MEASUREMENT") && (r.deviceId()==null || !parent.getResultType().equals("NUMERIC"))) throw new BizException(40001,"测量范围必须绑定设备及定量项目");
        if(parent.getResultType().equals("NUMERIC")){
            if(r.lowerBound()==null || r.upperBound()==null || r.lowerBound().compareTo(r.upperBound())>0 || (r.normalText()!=null && !r.normalText().isBlank())) throw new BizException(40001,"定量区间必须填写有效上下限，不能填写定性参考结果");
        } else if(r.normalText()==null || r.normalText().isBlank() || r.lowerBound()!=null || r.upperBound()!=null) throw new BizException(40001,"定性区间必须填写参考结果，不能填写数值上下限");
        v.setItemId(parent.getId());v.setDeviceId(r.deviceId());v.setKind(r.kind());v.setMethodology(parent.getMethodology());v.setUnit(parent.getUnit());v.setResultType(parent.getResultType());v.setSex(r.sex());v.setMinAgeDays(r.minAgeDays());v.setMaxAgeDays(r.maxAgeDays());v.setLowerBound(r.lowerBound());v.setUpperBound(r.upperBound());v.setNormalText(parent.getResultType().equals("NUMERIC")?null:r.normalText().trim());v.setValidFrom(r.validFrom());v.setValidTo(r.validTo());v.setActive(r.active());v.setUpdatedAt(LocalDateTime.now());
    }
    private TestItemVO vo(LabTestItem v){return new TestItemVO(v.getId(),v.getCode(),v.getName(),v.getMethodology(),v.getResultType(),v.getUnit(),v.getActive(),v.getVersion());}
    private IntervalVO vo(LabInterval v,LabTestItem parent){
        if(parent==null) throw new BizException(40900,"关联检验项目缺失，请核对基础资料");
        return new IntervalVO(v.getId(),v.getItemId(),parent.getCode(),parent.getName(),v.getDeviceId(),v.getKind(),v.getMethodology(),v.getUnit(),v.getResultType(),v.getSex(),v.getMinAgeDays(),v.getMaxAgeDays(),v.getLowerBound(),v.getUpperBound(),v.getNormalText(),v.getValidFrom(),v.getValidTo(),v.getActive(),v.getVersion());
    }
}
