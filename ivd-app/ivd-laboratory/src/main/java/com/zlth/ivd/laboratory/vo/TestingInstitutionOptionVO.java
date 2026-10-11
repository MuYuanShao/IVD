package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record TestingInstitutionOptionVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String code,String name,String type) { }
