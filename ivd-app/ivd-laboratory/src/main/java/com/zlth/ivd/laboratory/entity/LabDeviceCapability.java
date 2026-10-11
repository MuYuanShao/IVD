package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_device_capability")
public class LabDeviceCapability extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long deviceId;
    private Long itemId;
    private Boolean active;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
