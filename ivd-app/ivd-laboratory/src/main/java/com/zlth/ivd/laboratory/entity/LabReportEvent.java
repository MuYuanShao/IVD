package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_report_event")
public class LabReportEvent extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long reportId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String encryptedSnapshot;
    private Long actorId;
    private LocalDateTime occurredAt;
}
