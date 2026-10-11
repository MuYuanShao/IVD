package com.zlth.ivd.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String deviceCode;
    private String name;
    private String model;
    private String manufacturer;
    private Long locationId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @TableField(select = false, insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long institutionId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @TableField(select = false, insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long deptId;
    /** Explicit registry query maps this field; legacy device reads do not require the new column. */
    @TableField(exist=false) private Integer version;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDate lastCalibrationDate;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDate nextCalibrationDate;
    private Boolean isActive;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
