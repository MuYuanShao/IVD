package com.zlth.ivd.laboratory.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.security.InstitutionOwnedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("lab_report_result")
public class LabReportResult extends InstitutionOwnedEntity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long departmentId;
    private Long reportId;
    private Long resultId;
    private Integer resultVersion;
}
