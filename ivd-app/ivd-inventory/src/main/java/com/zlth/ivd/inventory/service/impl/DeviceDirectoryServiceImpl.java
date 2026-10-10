package com.zlth.ivd.inventory.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.entity.Device;
import com.zlth.ivd.inventory.mapper.DeviceMapper;
import com.zlth.ivd.inventory.service.DeviceDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class DeviceDirectoryServiceImpl implements DeviceDirectoryService {
    private final DeviceMapper mapper;
    @Override public DeviceReference requireActiveOwned(Long id) {
        return load(id,true);
    }
    @Override public DeviceReference requireOwned(Long id) { return load(id,false); }
    private DeviceReference load(Long id,boolean activeOnly) {
        var query = ownedQuery().eq(Device::getId,id);
        if(activeOnly) query.eq(Device::getIsActive,true);
        var device = mapper.selectOne(query);
        if(device==null) throw new BizException(40400,"设备不存在、已停用或无权访问");
        return new DeviceReference(device.getId(),device.getDeviceCode(),device.getName(),device.getDeptId());
    }
    @Override public java.util.List<DeviceReference> activeOptions(String keyword) {
        var query = ownedQuery().eq(Device::getIsActive,true);
        if(keyword!=null && !keyword.isBlank()) query.and(q->q.like(Device::getDeviceCode,keyword.trim()).or().like(Device::getName,keyword.trim()));
        query.orderByAsc(Device::getDeviceCode).last("LIMIT 50");
        return mapper.selectList(query).stream().map(device->new DeviceReference(device.getId(),device.getDeviceCode(),device.getName(),device.getDeptId())).toList();
    }
    private LambdaQueryWrapper<Device> ownedQuery() {
        var scope=InstitutionContext.require();
        var query=new LambdaQueryWrapper<Device>().eq(Device::getInstitutionId,scope.institutionId())
                .select(Device::getId,Device::getDeviceCode,Device::getName,Device::getDeptId);
        if(java.util.Set.of("LAB_TECH","LAB_DOCTOR","LAB_DIRECTOR").contains(scope.role())) {
            if(scope.departmentId()==null) throw new BizException(40300,"设备访问需要所属科室");
            query.and(q->q.isNull(Device::getDeptId).or().eq(Device::getDeptId,scope.departmentId()));
        }
        return query;
    }
}
