package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
/** This is a draft result, not a signed report. No diagnosis is generated. */
public record ResultDetailVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        @JsonSerialize(using=ToStringSerializer.class) Long sampleId,
        @JsonSerialize(using=ToStringSerializer.class) Long patientId,
        @JsonSerialize(using=ToStringSerializer.class) Long itemId,
        @JsonSerialize(using=ToStringSerializer.class) Long deviceId,
        @JsonSerialize(using=ToStringSerializer.class) Long testingInstitutionId,
        String executionMode,String result,String unit,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS") LocalDateTime testedAt,Integer version,
        String itemCode,String itemName,String deviceName,String testingInstitutionName,
        String referenceStatus,String referenceReason,String measurementStatus,String measurementReason,
        IntervalVO referenceInterval,IntervalVO measurementInterval) { }
