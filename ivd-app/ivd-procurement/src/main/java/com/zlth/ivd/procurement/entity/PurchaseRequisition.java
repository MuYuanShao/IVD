package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.UrgencyEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_requisition")
public class PurchaseRequisition {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String reqNo;
    private Integer fromDeptId;
    private Integer toWarehouseId;
    private Integer applicantId;
    private UrgencyEnum urgency;
    private RequisitionStatusEnum status;
    private Integer reviewerId;
    private LocalDateTime reviewTime;
    private String reviewComment;
    private String remark;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
