package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record TestItemVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String code,String name,String methodology,String resultType,String unit,Boolean active,Integer version) { }
