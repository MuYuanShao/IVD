package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
public record ReportHistoryVO(String action,String fromStatus,String toStatus,
        @JsonSerialize(using=ToStringSerializer.class) Long actorId,LocalDateTime occurredAt,String reason) { }
