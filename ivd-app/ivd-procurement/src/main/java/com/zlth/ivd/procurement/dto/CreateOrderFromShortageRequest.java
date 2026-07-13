package com.zlth.ivd.procurement.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 从缺货单生成采购订单请求 DTO
 *
 * @author IVD Team
 * @since 2026-07-07
 */
public class CreateOrderFromShortageRequest {

    @NotNull(message = "缺货单ID不能为空")
    private Long shortageId;

    @NotNull(message = "供应商ID不能为空")
    private Integer supplierId;

    private String expectedDeliveryDate;
    private String paymentTerms;
    private String remark;

    public Long getShortageId() { return shortageId; }
    public void setShortageId(Long shortageId) { this.shortageId = shortageId; }

    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }

    public String getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(String expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
