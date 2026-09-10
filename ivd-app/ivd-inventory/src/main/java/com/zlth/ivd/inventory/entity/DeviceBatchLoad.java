package com.zlth.ivd.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device_batch_load")
public class DeviceBatchLoad {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long deviceId;
    private Long batchId;
    private String batchNo;
    private Long reagentId;
    private Integer operatorId;
    @TableField("load_time") private LocalDateTime loadedAt;
    @TableField("unload_time") private LocalDateTime unloadedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
