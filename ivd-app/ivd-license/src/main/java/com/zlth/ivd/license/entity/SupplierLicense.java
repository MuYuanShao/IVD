package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zlth.ivd.common.enums.LicenseStatusEnum;
import com.zlth.ivd.common.enums.LicenseTypeEnum;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照主表实体
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@TableName("supplier_license")
public class SupplierLicense {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long supplierId;
    private LicenseTypeEnum licenseType;
    private String certNo;
    private String issuingAuthority;
    private LocalDate issueDate;
    private LocalDate expireDate;
    private LicenseStatusEnum status;
    private String attachments; // JSON

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
