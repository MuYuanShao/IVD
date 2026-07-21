package com.zlth.ivd.license.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试剂-供应商关联实体
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
@TableName("reagent_supplier_rel")
public class ReagentSupplierRel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long reagentId;
    private Long supplierId;
    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
