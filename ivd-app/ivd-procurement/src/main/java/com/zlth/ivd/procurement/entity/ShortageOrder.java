package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.UrgencyEnum;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shortage_order")
public class ShortageOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String shortageNo;
    private Long reqId;
    private Integer fromDeptId;
    private ShortageStatusEnum status;
    private UrgencyEnum urgency;
    private Integer suggestedSupplierId;
    private Long poId;
    private Integer createdBy;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
