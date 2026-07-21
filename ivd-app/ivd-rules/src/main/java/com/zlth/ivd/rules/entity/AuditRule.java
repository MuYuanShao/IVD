package com.zlth.ivd.rules.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_rule")
public class AuditRule {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String ruleId;              // R-RST-001 etc.
    private String name;
    private String layer;               // RESULT / BATCH / LINKAGE
    private Integer priority;
    private Integer enabled;
    private String scope;               // JSON
    private String ruleCondition;       // JSON
    private String action;              // AUTO_RELEASE/WARN_RELEASE/HOLD_REVIEW/REJECT_RETEST
    private String reasonTemplate;
    private String linkTo;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
