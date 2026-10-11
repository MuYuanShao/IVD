package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_result_evidence")
public class LabResultEvidence extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long resultId;
    private Integer resultVersion;
    private Long batchId;
    private Long deviceLoadId;
    private Long consumptionId;
    private String evidenceKey;
    private String encryptedSnapshot;
    private Long actorId;
    private LocalDateTime createdAt;
}
