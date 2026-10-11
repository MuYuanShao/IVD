package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_import_batch")
public class LabImportBatch extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long actorId;
    private String resource;
    private String contentIndex;
    private String status;
    private String encryptedDocument;
    private String encryptedOutcome;
    private Integer rowCount;
    private Integer succeeded;
    private LocalDateTime expiresAt;
    private String executionKey;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
