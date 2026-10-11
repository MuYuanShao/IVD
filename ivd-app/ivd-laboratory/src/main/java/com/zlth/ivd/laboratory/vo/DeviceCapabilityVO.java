package com.zlth.ivd.laboratory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record DeviceCapabilityVO(@JsonSerialize(using=ToStringSerializer.class) Long deviceId,
        @JsonSerialize(using=ToStringSerializer.class) Long itemId,String itemCode,String itemName,boolean active,Integer version) { }
