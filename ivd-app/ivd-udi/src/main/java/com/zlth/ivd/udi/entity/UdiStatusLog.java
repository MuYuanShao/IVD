package com.zlth.ivd.udi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UDI 状态流转日志（不可变，APPEND-ONLY）
 * <p>
 * 仅适用于 material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL}
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Data
@Builder
@TableName("udi_status_log")
public class UdiStatusLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 转移前状态 */
    private String fromStatus;

    /** 转移后状态 */
    private String toStatus;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 转移发生时间 */
    private LocalDateTime occurredAt;

    private LocalDateTime createdAt;
}
