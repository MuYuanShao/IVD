package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_sample_event")
public class LabSampleEvent extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long sampleId;
    private String fromStatus;
    private String toStatus;
    private String encryptedReason;
    private Long actorId;
    private LocalDateTime occurredAt;
}
