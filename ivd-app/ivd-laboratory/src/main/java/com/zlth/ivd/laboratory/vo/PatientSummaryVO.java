package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record PatientSummaryVO(@JsonSerialize(using=ToStringSerializer.class) Long id,
        String maskedNumber,String maskedName,Integer version) { }
