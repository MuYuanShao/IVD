package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import com.zlth.ivd.laboratory.enums.ReportStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_report")
public class LabReport extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long sampleId;
    private Long patientId;
    private Long rootId;
    private Integer edition;
    private String numberIndex;
    private ReportStatusEnum status;
    private String encryptedSnapshot;
    @Version private Integer version;
    private Long createdBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long reviewedBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long signedBy;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime signedAt;
    private String pdfKey;
    private String pdfDigest;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
