package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("smart_replenish_run")
public class SmartReplenishRun {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Integer analysisDays;
    private String batchNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
