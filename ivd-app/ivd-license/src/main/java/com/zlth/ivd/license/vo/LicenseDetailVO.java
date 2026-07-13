package com.zlth.ivd.license.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 证照明细 VO — 含关联试剂 + 附件 + 变更记录
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@Builder
public class LicenseDetailVO {

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

    // 关联试剂列表
    private List<RelReagent> relatedReagents;

    // 附件列表
    private List<AttachmentItem> attachments;

    // 变更记录
    private List<ChangeLogItem> changeLogs;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联试剂 */
    @Data
    @Builder
    public static class RelReagent {
        private Long reagentId;
        private String hisCode;
        private String genericName;
        private String manufacturer;
        private Boolean isDefault;
    }

    /** 附件项 */
    @Data
    @Builder
    public static class AttachmentItem {
        private String name;
        private String objectKey;
        private Long size;
        private String type;
        private String uploadedAt;
        private String uploadedBy;
    }

    /** 变更记录项 */
    @Data
    @Builder
    public static class ChangeLogItem {
        private Long id;
        private String changeType;
        private String changeTypeLabel;
        private LocalDate oldExpireDate;
        private LocalDate newExpireDate;
        private String operatorName;
        private String note;
        private LocalDateTime createdAt;
    }
}
