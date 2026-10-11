package com.zlth.ivd.inventory.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Batch identity stays textual to retain BIGINT precision in browsers. */
@Data
public class RemainingBatchVO {
    private String id;
    private String code;
    private String name;
    private String manufacturer;
    private String specification;
    private String batchNo;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private String warehouse;
    private Integer warehouseLevel;
    private String location;
    private String status;
    private LocalDateTime updatedAt;
}
