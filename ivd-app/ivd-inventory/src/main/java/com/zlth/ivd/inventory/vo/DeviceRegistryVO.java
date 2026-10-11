package com.zlth.ivd.inventory.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
public record DeviceRegistryVO(@JsonSerialize(using=ToStringSerializer.class) Long id,String code,String name,String model,String manufacturer,
        @JsonSerialize(using=ToStringSerializer.class) Long locationId,@JsonSerialize(using=ToStringSerializer.class) Long departmentId,
        Boolean active,LocalDate lastCalibrationDate,LocalDate nextCalibrationDate,Integer version) { }
