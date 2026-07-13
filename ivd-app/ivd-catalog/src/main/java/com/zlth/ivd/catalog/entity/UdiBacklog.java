package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UDI 待补全追踪表 Entity
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
@TableName("udi_backlog")
public class UdiBacklog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联试剂ID（唯一） */
    private Long reagentId;

    /** 物料类型（仅 REAGENT / CALIBRATOR / QC_MATERIAL） */
    private MaterialTypeEnum materialType;

    /** 影响级别：BLOCK（阻断采购）/ WARN（预警） */
    private String impactLevel;

    /** 是否存在进行中的采购订单 */
    private Boolean hasOpenPo;

    /** 待补全天数 */
    private Integer daysPending;

    /** 最近通知时间 */
    private LocalDateTime notifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
