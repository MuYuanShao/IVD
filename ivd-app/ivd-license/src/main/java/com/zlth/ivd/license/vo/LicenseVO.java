package com.zlth.ivd.license.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照列表 VO
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@Builder
public class LicenseVO {

    private Long id;
    private Long supplierId;
    private String supplierName;
    private String licenseType;
    private String licenseTypeLabel;
    private String certNo;
    private String issuingAuthority;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private String status;
    private String statusLabel;
    private Long remainingDays;
    private Integer attachmentCount;
    private String relatedReagents; // 关联试剂名称，逗号分隔
    private LocalDateTime createdAt;
}
