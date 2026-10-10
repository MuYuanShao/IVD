package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record IntervalEvaluationVO(String status,@JsonSerialize(using=ToStringSerializer.class) Long intervalId,String reason) { }
