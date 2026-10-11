package com.zlth.ivd.laboratory.dto;
import jakarta.validation.constraints.*;
public record ReportActionRequest(@NotNull @Min(0) Integer version,@Size(max=1000) String reason,Boolean acknowledgeRisks) { }
