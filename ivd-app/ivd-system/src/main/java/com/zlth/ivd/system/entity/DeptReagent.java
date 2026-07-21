package com.zlth.ivd.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 科室试剂目录关联实体 — M00-2 扩展
 *
 * @author IVD Team
 * @since 2026-07-15
 */
@Data
@TableName("dept_reagent")
public class DeptReagent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 科室ID */
    private Long deptId;

    /** 试剂ID（reagent_catalog.id） */
    private Long reagentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
