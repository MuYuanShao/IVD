package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.UrgencyEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("purchase_requisition")
public class PurchaseRequisition {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String reqNo;
    private Long fromDeptId;
    private Long toWarehouseId;
    private Long applicantId;
    private UrgencyEnum urgency;
    private RequisitionStatusEnum status;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewComment;
    private String remark;
    private Long researchProjectId;
    private String researchProjectName;

    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;

    /** 申领明细（非数据库字段，创建时传入并持久化到 purchase_requisition_item） */
    @TableField(exist = false)
    private List<PurchaseRequisitionItem> items;
}
