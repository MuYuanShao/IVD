package com.zlth.ivd.common.base;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类 — 所有业务主表 Entity 继承此类
 * <p>
 * 包含标准审计字段：created_at, updated_at
 * 审批类实体额外包含：submitted_by, submitted_at, approved_by, approved_at
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
