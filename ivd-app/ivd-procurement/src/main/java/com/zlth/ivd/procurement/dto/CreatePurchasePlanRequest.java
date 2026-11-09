package com.zlth.ivd.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchasePlanRequest {
    @Pattern(regexp="MANUAL|SHORTAGE|SMART_REPLENISH")
    private String sourceType="MANUAL";
    private Long shortageId;
    private Integer supplierId;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String settlementMode;
    @Size(max=1000)
    private String remark;
    @Valid @Size(max=500)
    private List<Item> items;
    @Valid @Size(max=500)
    private List<Suggestion> suggestions;
    @Data public static class Item {
        @NotNull private Long reagentId;
        private Integer supplierId;
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=2)
        private BigDecimal quantity;
    }
    @Data public static class Suggestion {
        @NotNull private Long id;
        @NotNull private Long reagentId;
        private Integer supplierId;
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=8,fraction=2)
        private BigDecimal suggestedQty;
    }
}
