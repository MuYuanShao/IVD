package com.zlth.ivd.procurement.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 手动创建采购订单请求 DTO — 支持同时录入采购明细
 *
 * @author IVD Team
 * @since 2026-07-20
 */
public class CreateManualOrderRequest {

    @NotNull(message = "供应商ID不能为空")
    private Integer supplierId;

    private String orderDate;
    private String expectedDeliveryDate;
    private String settlementMode;
    private String remark;
    private List<ManualOrderItem> items;

    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(String expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getSettlementMode() { return settlementMode; }
    public void setSettlementMode(String settlementMode) { this.settlementMode = settlementMode; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<ManualOrderItem> getItems() { return items; }
    public void setItems(List<ManualOrderItem> items) { this.items = items; }

    /**
     * 手工采购明细项
     */
    public static class ManualOrderItem {
        @NotNull(message = "试剂ID不能为空")
        private Integer reagentId;
        @NotNull(message = "采购数量不能为空")
        private BigDecimal quantity;

        public Integer getReagentId() { return reagentId; }
        public void setReagentId(Integer reagentId) { this.reagentId = reagentId; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    }
}
