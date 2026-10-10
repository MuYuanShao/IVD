package com.zlth.ivd.common.audit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OperationAuditEntry {
    private Long operatorId;
    private String operatorName;
    private String module;
    private String action;
    private String requestMethod;
    private String requestPath;
    private String handler;
    private String clientIp;
    private String reason;
    private Boolean success;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime occurredAt;
}
