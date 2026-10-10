package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import java.time.LocalDateTime;
import lombok.*;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_patient_department")
public class LabPatientDepartment extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long patientId;
    private Long departmentId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
