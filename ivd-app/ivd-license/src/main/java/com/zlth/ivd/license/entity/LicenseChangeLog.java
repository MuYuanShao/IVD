package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证照变更记录实体（APPEND-ONLY）
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@TableName("license_change_log")
public class LicenseChangeLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long licenseId;
    private String changeType;
    private LocalDate oldExpireDate;
    private LocalDate newExpireDate;
    private Long operatorId;
    private String operatorName;
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
