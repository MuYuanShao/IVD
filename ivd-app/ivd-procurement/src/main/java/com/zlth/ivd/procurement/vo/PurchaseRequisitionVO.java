package com.zlth.ivd.procurement.vo;

import com.zlth.ivd.common.enums.RequisitionStatusEnum;
import com.zlth.ivd.common.enums.UrgencyEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 申领单 VO（含关联显示字段）
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class PurchaseRequisitionVO {
    private Long id;
    private String reqNo;
    private Integer fromDeptId;
    private String deptName;
    private Integer toWarehouseId;
    private Integer applicantId;
    private String applicantName;
    private UrgencyEnum urgency;
    private RequisitionStatusEnum status;
    private Integer reviewerId;
    private LocalDateTime reviewTime;
    private String reviewComment;
    private String remark;

    /** 汇总字段 */
    private Integer totalQty;
    private BigDecimal totalAmount;
    private Integer shippedQty;
    private Integer shortageQty;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
