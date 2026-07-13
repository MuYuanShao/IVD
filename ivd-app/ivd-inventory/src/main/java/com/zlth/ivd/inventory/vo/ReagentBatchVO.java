package com.zlth.ivd.inventory.vo;

import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 试剂批次 VO（含关联显示字段）
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Data
public class ReagentBatchVO {
    private Long id;
    private String batchNo;
    private Long reagentId;
    private String reagentName;
    private MaterialTypeEnum materialType;
    private String udiDi;
    private String udiPi;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private BigDecimal stock;
    private String unit;
    private BigDecimal unitPrice;
    private UdiStatusEnum udiStatus;
    private Long warehouseId;
    private String locationCode;
    private String location;
    private String inboundDate;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
