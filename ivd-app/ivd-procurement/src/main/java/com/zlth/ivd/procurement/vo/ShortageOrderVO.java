package com.zlth.ivd.procurement.vo;

import com.zlth.ivd.common.enums.ShortageStatusEnum;
import com.zlth.ivd.common.enums.UrgencyEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缺货单 VO（含关联显示字段）
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class ShortageOrderVO {
    private Long id;
    private String shortageNo;
    private Long reqId;
    private String sourceReqNo;
    private Integer fromDeptId;
    private String deptName;
    private String reagentName;
    private String spec;
    private Integer shortageQty;
    private java.math.BigDecimal estimatedAmount;
    private ShortageStatusEnum status;
    private UrgencyEnum urgency;
    private Integer suggestedSupplierId;
    private String suggestedSupplier;
    private Long poId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
