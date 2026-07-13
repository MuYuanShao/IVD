package com.zlth.ivd.license.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建证照请求
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
public class CreateLicenseRequest {

    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @NotBlank(message = "证照类型不能为空")
    private String licenseType;

    @NotBlank(message = "证号不能为空")
    private String certNo;

    private String issuingAuthority;

    @NotNull(message = "发证日期不能为空")
    private LocalDate issueDate;

    @NotNull(message = "有效期至不能为空")
    private LocalDate expireDate;

    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    private String operatorName;
}
