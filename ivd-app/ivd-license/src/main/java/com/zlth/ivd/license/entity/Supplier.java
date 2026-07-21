package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商主表实体
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 供应商编码（业务唯一标识，格式 SUP-YYYYMMDD-NNN） */
    private String supplierCode;

    private String name;
    private String shortName;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
