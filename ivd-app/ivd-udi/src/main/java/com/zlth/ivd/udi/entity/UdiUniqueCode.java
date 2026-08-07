package com.zlth.ivd.udi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * UDI 唯一码 — 验收入库时按入库数量逐盒/逐单位生成
 * 格式: yyyyMMdd + 6位日级自增序号，如 20260723000001
 *
 * @author IVD Team
 * @since 2026-07-23
 */
@Data
@TableName("udi_unique_code")
public class UdiUniqueCode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一码 yyyyMMdd + 6位序号 */
    private String uniqueCode;

    /** 关联批次ID */
    private Long batchId;

    /** 试剂ID */
    private Long reagentId;

    /** 批号（冗余） */
    private String batchNo;

    /** UDI-DI（冗余） */
    private String udiDi;

    /** 状态: IN_STOCK / TRANSFERRED / CONSUMED / EXPIRED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
