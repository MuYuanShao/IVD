package com.zlth.ivd.inventory.mapper;
import com.zlth.ivd.inventory.entity.Device;
import org.apache.ibatis.annotations.*;
/** Versioned registry writes leave legacy device mapper SQL unchanged. */
@Mapper public interface DeviceRegistryMapper {
    @Update("UPDATE device SET dept_id=#{department} WHERE id=#{id} AND institution_id=#{owner} AND version=0")
    int initializeDepartment(@Param("id") Long id,@Param("department") Long department,@Param("owner") long owner);
    @Update("UPDATE device SET device_code=#{value.deviceCode},name=#{value.name},model=#{value.model},manufacturer=#{value.manufacturer},location_id=#{value.locationId},dept_id=#{value.deptId},is_active=#{value.isActive},last_calibration_date=#{value.lastCalibrationDate},next_calibration_date=#{value.nextCalibrationDate},updated_at=#{value.updatedAt},version=version+1 WHERE id=#{value.id} AND institution_id=#{owner} AND version=#{value.version}")
    int updateVersioned(@Param("value") Device value,@Param("owner") long owner);
}
