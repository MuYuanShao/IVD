package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import com.zlth.ivd.laboratory.enums.SampleStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_sample")
public class LabSample extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long patientId;
    private String numberIndex;
    private String encryptedDetails;
    private SampleStatusEnum status;
    @Version private Integer version;
    private Long createdBy;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
