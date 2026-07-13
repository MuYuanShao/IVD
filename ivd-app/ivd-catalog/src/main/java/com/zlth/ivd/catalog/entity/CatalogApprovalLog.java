package com.zlth.ivd.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试剂目录审批日志 Entity
 * <p>
 * 主键使用雪花 ID（非自增），由应用层 {@link com.zlth.ivd.common.utils.IdGenerator} 生成。
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
@TableName("catalog_approval_log")
public class CatalogApprovalLog {

    /** 雪花 ID（应用层生成，非自增） */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 关联试剂ID */
    private Long reagentId;

    /** 变更类型：CREATE / UPDATE / DELETE / DISABLE */
    private String changeType;

    /** 字段变更内容（JSON 格式：old -> new） */
    private String fieldChanges;

    /** 变更原因 */
    private String changeReason;

    /** 申请人ID */
    private Long submitterId;

    /** 申请人姓名 */
    private String submitterName;

    /** 审批人ID */
    private Long reviewerId;

    /** 审批人姓名 */
    private String reviewerName;

    /** 审批状态：PENDING / APPROVED / REJECTED */
    private String reviewStatus;

    /** 审批意见（驳回时必填） */
    private String reviewComment;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 审批时间 */
    private LocalDateTime reviewedAt;
}
