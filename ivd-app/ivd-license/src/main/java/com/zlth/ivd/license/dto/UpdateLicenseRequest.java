package com.zlth.ivd.license.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新证照请求
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
public class UpdateLicenseRequest {

    private Long supplierId;
    private String licenseType;
    private String certNo;
    private String issuingAuthority;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private String status;

    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    private String operatorName;
    private String changeNote;
}
