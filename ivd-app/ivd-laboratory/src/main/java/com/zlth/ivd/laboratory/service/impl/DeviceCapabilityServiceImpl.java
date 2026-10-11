package com.zlth.ivd.laboratory.service.impl;
import com.zlth.ivd.laboratory.dto.DeviceCapabilityRequest;
import com.zlth.ivd.laboratory.vo.DeviceCapabilityVO;
import com.zlth.ivd.laboratory.service.DeviceCapabilityService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.service.DeviceDirectoryService;
import com.zlth.ivd.laboratory.entity.LabDeviceCapability;
import com.zlth.ivd.laboratory.mapper.LabDeviceCapabilityMapper;
import com.zlth.ivd.laboratory.security.*;
import com.zlth.ivd.laboratory.service.LabCatalogService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class DeviceCapabilityServiceImpl implements DeviceCapabilityService {
    private final LabDeviceCapabilityMapper mapper;
    private final LabCatalogService catalog;
    private final DeviceDirectoryService devices;
    private final ClinicalRecordSupport records;
    public DeviceCapabilityVO set(Long id,DeviceCapabilityRequest request){
        ClinicalAccess.requireMasterWriter();records.validate(request);devices.requireOwned(id);
        var item=catalog.lockItem(request.itemId(),request.active());devices.lockOwned(id,request.active());
        var value=mapper.selectOne(query(id).eq(LabDeviceCapability::getItemId,item.id()).last("FOR UPDATE"));
        if(value==null){
            if(request.version()!=null)throw new BizException(40900,"能力配置不存在，请刷新后重试");
            value=new LabDeviceCapability();value.setDeviceId(id);value.setItemId(item.id());value.setVersion(0);value.setActive(request.active());value.setCreatedAt(LocalDateTime.now());value.setUpdatedAt(value.getCreatedAt());
            try{mapper.insert(value);}catch(org.springframework.dao.DuplicateKeyException error){throw new BizException(40900,"能力配置已存在，请刷新后编辑");}
        }else{
            if(!Objects.equals(value.getVersion(),request.version()))throw new BizException(40900,"能力配置版本已变更");
            value.setActive(request.active());value.setUpdatedAt(LocalDateTime.now());if(mapper.updateById(value)!=1)throw new BizException(40900,"能力配置版本已变更");
        }
        records.audit("laboratory-capability","SET",value.getId());return new DeviceCapabilityVO(id,item.id(),item.code(),item.name(),Boolean.TRUE.equals(value.getActive()),value.getVersion());
    }
    public List<DeviceCapabilityVO> list(Long id){
        devices.requireOwned(id);
        return mapper.selectList(query(id)).stream().map(value->{var item=catalog.item(value.getItemId());return new DeviceCapabilityVO(id,item.id(),item.code(),item.name(),Boolean.TRUE.equals(value.getActive()),value.getVersion());}).toList();
    }
    public void requireExecutable(Long deviceId,Long itemId){
        devices.lockOwned(deviceId,true);
        var capability=mapper.selectOne(query(deviceId).eq(LabDeviceCapability::getItemId,itemId).eq(LabDeviceCapability::getActive,true).last("FOR UPDATE"));
        if(capability==null)
            throw new BizException(40900,"设备未配置可执行该项目的启用能力，不能录入仪器结果");
    }
    private LambdaQueryWrapper<LabDeviceCapability> query(Long id){return new LambdaQueryWrapper<LabDeviceCapability>().eq(LabDeviceCapability::getInstitutionId,InstitutionContext.require().institutionId()).eq(LabDeviceCapability::getDeviceId,id);}
}
