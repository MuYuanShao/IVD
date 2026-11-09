package com.zlth.ivd.procurement.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PurchasePlanReviewRequest {
    @Size(max=1000)
    private String comment;
}
