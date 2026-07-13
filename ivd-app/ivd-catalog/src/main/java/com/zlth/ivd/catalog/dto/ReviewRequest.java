package com.zlth.ivd.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批操作请求 DTO（通过 / 驳回共用）
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class ReviewRequest {

    /** 审批人ID（优先从认证上下文获取；未认证时使用请求体值） */
    private Long reviewerId;

    /** 审批人姓名（优先从认证上下文获取；未认证时使用请求体值） */
    private String reviewerName;

    /** 审批意见（驳回时必填，Service 层校验） */
    private String comment;
}
