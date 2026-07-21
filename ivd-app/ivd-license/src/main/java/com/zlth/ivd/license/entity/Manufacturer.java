package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生产厂家主表实体
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
@TableName("manufacturer")
public class Manufacturer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 厂家编码（业务唯一标识，格式 MFG-YYYYMMDD-NNN） */
    private String manufacturerCode;

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
