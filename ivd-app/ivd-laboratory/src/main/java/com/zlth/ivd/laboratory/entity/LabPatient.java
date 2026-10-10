package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import java.time.LocalDateTime;
import lombok.*;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_patient")
public class LabPatient extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private String numberIndex;
    private String encryptedProfile;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
