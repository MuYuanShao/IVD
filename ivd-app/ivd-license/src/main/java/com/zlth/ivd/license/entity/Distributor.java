package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配送商主表实体
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Data
@TableName("distributor")
public class Distributor {

    @TableId(type = IdType.AUTO)
    private Long id;
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
