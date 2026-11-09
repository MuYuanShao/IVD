package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("purchase_plan_event")
public class PurchasePlanEvent {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long planId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String reason;
    private String snapshot;
    private LocalDateTime createdAt;
}
