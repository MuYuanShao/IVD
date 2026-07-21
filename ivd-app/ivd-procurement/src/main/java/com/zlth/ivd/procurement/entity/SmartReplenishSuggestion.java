package com.zlth.ivd.procurement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能补货建议 — 持久化计算后的补货建议，支持数量修改后批量生成采购单
 *
 * @author IVD Team
 * @since 2026-07-20
 */
@Data
@TableName("smart_replenish_suggestion")
public class SmartReplenishSuggestion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long reagentId;
    private String reagentName;
    private String specification;
    private String manufacturer;
    private BigDecimal currentStock;
    private BigDecimal dailyConsumption;
    private BigDecimal safetyStock;
    private BigDecimal suggestedQty;
    private String unit;
    private String supplier;
    private Integer analysisDays;
    private String batchNo;
    private String status;          // PENDING / GENERATED / CANCELLED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
