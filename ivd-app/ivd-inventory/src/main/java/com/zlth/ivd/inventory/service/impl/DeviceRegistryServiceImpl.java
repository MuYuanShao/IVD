package com.zlth.ivd.inventory.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.*;
import com.zlth.ivd.inventory.dto.DeviceRegistryRequest;
import com.zlth.ivd.inventory.vo.DeviceRegistryVO;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.mapper.*;
import com.zlth.ivd.inventory.service.*;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class DeviceRegistryServiceImpl implements DeviceRegistryService {
    private final DeviceMapper mapper;
    private final DeviceRegistryMapper writes;
    private final WarehouseMapper warehouses;
    private final DeviceDirectoryService directory;
    private final OwnedDepartmentService departments;
    private final Validator validator;
    private final com.zlth.ivd.common.audit.OperationAuditLogWriter audits;
    public List<com.zlth.ivd.inventory.vo.DeviceWarehouseOptionVO> warehouseOptions(){
        var scope=InstitutionContext.require();InstitutionContext.requireUserActor();var q=new QueryWrapper<Warehouse>().select("id","name","dept_id","level").eq("institution_id",scope.institutionId()).eq("status",1).eq("is_active",true);
        if(!scope.role().equals("SYSTEM_ADMIN")){if(scope.departmentId()==null)throw new BizException(40300,"库房访问需要科室");q.and(w->w.eq("level",1).or().eq("dept_id",scope.departmentId()).or().inSql("parent_id","SELECT id FROM warehouse WHERE institution_id="+scope.institutionId()+" AND dept_id="+scope.departmentId()));}
        return warehouses.selectList(q.orderByAsc("id")).stream().map(w->new com.zlth.ivd.inventory.vo.DeviceWarehouseOptionVO(w.getId(),w.getName(),w.getDeptId(),w.getLevel())).toList();
    }
    private QueryWrapper<Device> query(){return new QueryWrapper<Device>().select("id","device_code","name","model","manufacturer","location_id","dept_id","last_calibration_date","next_calibration_date","is_active","version","created_at","updated_at").eq("institution_id",InstitutionContext.require().institutionId());}
    public DeviceRegistryVO detail(Long id){directory.requireOwned(id);var row=mapper.selectOne(query().eq("id",id));if(row==null)throw new BizException(40400,"设备不存在或无权访问");return vo(row);}
    public PageVO<DeviceRegistryVO> list(int page,int size,String keyword){
        InstitutionContext.requireUserActor();var q=query();var scope=InstitutionContext.require();
        if(!scope.role().equals("SYSTEM_ADMIN")){if(scope.departmentId()==null)throw new BizException(40300,"设备访问需要科室");q.and(w->w.isNull("dept_id").or().eq("dept_id",scope.departmentId()));}
        if(keyword!=null && !keyword.isBlank())q.and(w->w.like("device_code",keyword.trim()).or().like("name",keyword.trim()));
        var result=mapper.selectPage(new Page<Device>(Math.max(1,page),Math.min(100,Math.max(1,size))),q.orderByDesc("id"));return new PageVO<>(result.getTotal(),result.getCurrent(),result.getSize(),result.getRecords().stream().map(this::vo).toList());
    }
    public DeviceRegistryVO create(DeviceRegistryRequest request){requireWriter();validate(request);var row=new Device();copy(row,request);row.setVersion(0);row.setCreatedAt(LocalDateTime.now());row.setUpdatedAt(row.getCreatedAt());try{mapper.insert(row);if(writes.initializeDepartment(row.getId(),row.getDeptId(),InstitutionContext.require().institutionId())!=1)throw new BizException(40900,"设备初始化冲突");}catch(org.springframework.dao.DuplicateKeyException error){throw new BizException(40900,"设备编码已存在");}audit("CREATE",row.getId());return vo(row);}
    public DeviceRegistryVO update(Long id,DeviceRegistryRequest request){
        requireWriter();validate(request);directory.requireOwned(id);var row=mapper.selectOne(query().eq("id",id).last("FOR UPDATE"));if(row==null)throw new BizException(40400,"设备不存在");
        if(!Objects.equals(row.getVersion(),request.version()))throw new BizException(40900,"设备版本已变更");copy(row,request);row.setUpdatedAt(LocalDateTime.now());try{if(writes.updateVersioned(row,InstitutionContext.require().institutionId())!=1)throw new BizException(40900,"设备版本已变更");row.setVersion(row.getVersion()+1);}catch(org.springframework.dao.DuplicateKeyException error){throw new BizException(40900,"设备编码已存在");}audit("UPDATE",row.getId());return vo(row);
    }
    private void validate(DeviceRegistryRequest r){if(r==null || !validator.validate(r).isEmpty())throw new BizException(40001,"设备字段无效");if(r.lastCalibrationDate()!=null && r.nextCalibrationDate()!=null && r.nextCalibrationDate().isBefore(r.lastCalibrationDate()))throw new BizException(40001,"下次校准日期不能早于上次校准日期");}
    private void requireWriter(){var scope=InstitutionContext.require();if(!Set.of("SYSTEM_ADMIN","LAB_DIRECTOR").contains(scope.role()))throw new BizException(40300,"没有设备维护权限");InstitutionContext.requireUserActor();}
    private void copy(Device row,DeviceRegistryRequest r){
        var scope=InstitutionContext.require();Long department=r.departmentId();Long inherited=null;Long location=r.locationId();var visited=new HashSet<Long>();
        for(int depth=0;location!=null && depth<3;depth++){
            if(!visited.add(location))throw new BizException(40900,"库房层级存在循环");
            var w=warehouses.selectOne(new QueryWrapper<Warehouse>().select("id","dept_id","level","parent_id","status","is_active").eq("id",location).eq("institution_id",scope.institutionId()));
            if(w==null || !Boolean.TRUE.equals(w.getIsActive()) || !Objects.equals(w.getStatus(),1))throw new BizException(40400,"设备库房不存在、已停用或无权访问");
            if(inherited==null)inherited=w.getDeptId();location=w.getParentId();
        }
        if(location!=null)throw new BizException(40900,"设备库房层级超出三级范围");
        if(department==null)department=inherited;if(inherited!=null && !Objects.equals(inherited,department))throw new BizException(40900,"设备科室与库房归属不一致");
        if(department!=null && !departments.isActiveOwned(department,scope.institutionId()))throw new BizException(40400,"设备科室不存在、已停用或归属不一致");
        if(scope.role().equals("LAB_DIRECTOR") && (scope.departmentId()==null || (department!=null && !Objects.equals(department,scope.departmentId()))))throw new BizException(40300,"不能管理其他科室设备");
        row.setDeviceCode(r.code().trim());row.setName(r.name().trim());row.setModel(r.model());row.setManufacturer(r.manufacturer());row.setLocationId(r.locationId());row.setDeptId(department);row.setIsActive(r.active());row.setLastCalibrationDate(r.lastCalibrationDate());row.setNextCalibrationDate(r.nextCalibrationDate());
    }
    private DeviceRegistryVO vo(Device row){return new DeviceRegistryVO(row.getId(),row.getDeviceCode(),row.getName(),row.getModel(),row.getManufacturer(),row.getLocationId(),row.getDeptId(),row.getIsActive(),row.getLastCalibrationDate(),row.getNextCalibrationDate(),row.getVersion());}
    private void audit(String action,Long id){audits.appendDomain(com.zlth.ivd.common.audit.OperationAuditEntry.builder().operatorId(InstitutionContext.requireUserActor()).operatorName(com.zlth.ivd.common.utils.SecurityUtils.getCurrentUsername()).module("device-registry").action(action).reason("Device ID="+id).success(true).durationMs(0L).occurredAt(LocalDateTime.now()).build());}
}
