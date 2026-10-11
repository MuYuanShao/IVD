package com.zlth.ivd.inventory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record DeviceWarehouseOptionVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String name,
        @JsonSerialize(using=ToStringSerializer.class) Long departmentId,int level) { }
