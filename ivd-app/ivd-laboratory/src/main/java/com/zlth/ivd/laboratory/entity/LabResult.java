package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import com.zlth.ivd.laboratory.enums.ExecutionModeEnum;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_result")
public class LabResult extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long sampleId;
    private Long itemId;
    // Switching an instrument draft to manual/external must actually clear the former device.
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long deviceId;
    private Long testingInstitutionId;
    private ExecutionModeEnum executionMode;
    private String encryptedSnapshot;
    @Version private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
