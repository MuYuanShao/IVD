package com.zlth.ivd.laboratory.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SampleSummaryVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        @JsonSerialize(using=ToStringSerializer.class) Long patientId,
        String maskedNumber,String status,Integer version) { }
