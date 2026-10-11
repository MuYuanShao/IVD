package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record ReportSummaryVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String maskedNumber,int edition,String status,int version,
        @JsonSerialize(using=ToStringSerializer.class) Long sampleId) { }
