package com.zlth.ivd.laboratory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.laboratory.entity.LabPatientDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface LabPatientDepartmentMapper extends BaseMapper<LabPatientDepartment> {
    @Select("SELECT * FROM lab_patient_department WHERE patient_id=#{patientId} AND institution_id=#{institutionId} AND department_id=#{departmentId} AND active=1 FOR UPDATE")
    LabPatientDepartment lockActive(@Param("patientId") Long patientId,@Param("institutionId") long institutionId,@Param("departmentId") Long departmentId);
}
