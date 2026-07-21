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
    private LocalDate lastCalibrationDate;
    private LocalDate nextCalibrationDate;
    private Boolean isActive;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
