package com.zlth.ivd.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交审批请求 DTO
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class SubmitApprovalRequest {

    @NotBlank(message = "变更原因不能为空")
    private String changeReason;

    /** 申请人ID（优先从认证上下文获取；未认证时使用请求体值） */
    private Long submitterId;

    /** 申请人姓名（优先从认证上下文获取；未认证时使用请求体值） */
    private String submitterName;
}
