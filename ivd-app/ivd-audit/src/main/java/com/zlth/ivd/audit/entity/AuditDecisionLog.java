package com.zlth.ivd.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("audit_decision_log")
public class AuditDecisionLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String observationId;
    private String patientIdHash;
    private String testItemCode;
    private BigDecimal testValue;
    private String deviceId;
    private LocalDateTime testDatetime;
    private String batchNo;
    private String decision;            // AUTO_RELEASE/WARN_RELEASE/HOLD_REVIEW/REJECT_RETEST
    private String triggeredRules;      // JSON array of rule IDs
    private Integer auditLatencyMs;
    private String manualDecision;      // RELEASED/HELD/RETEST
    private Integer auditorId;
    private String auditorNote;
    private BigDecimal recheckValue;
    private LocalDateTime decidedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
