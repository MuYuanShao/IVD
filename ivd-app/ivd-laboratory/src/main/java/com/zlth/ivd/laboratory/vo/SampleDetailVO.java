package com.zlth.ivd.laboratory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record SampleDetailVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        @JsonSerialize(using=ToStringSerializer.class) Long patientId,
        String number,String specimenType,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS") LocalDateTime collectedAt,
        String status,Integer version,@com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS") LocalDateTime receivedAt) { }
