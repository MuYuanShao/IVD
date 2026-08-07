package com.zlth.ivd.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统一变更记录 VO — 覆盖证照管理 + 试剂目录两模块
 *
 * @author IVD Team
 * @since 2026-07-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogVO {

    private String module;             // LICENSE / CATALOG
    private String moduleLabel;        // 证照管理 / 试剂目录

    private Long id;
    private Long licenseId;            // 证照ID（LICENSE 模块）
    private String supplierName;       // JOIN supplier.name
    private String certNo;             // JOIN supplier_license.cert_no
    private String licenseType;        // 证照类型ENUM值（内部映射用）
    private String licenseTypeLabel;   // 证照类型中文标签（转换后）

    // 试剂目录专用
    private Long reagentId;            // 试剂ID（CATALOG 模块）
    private String reagentName;        // 试剂名称（JOIN reagent_catalog.generic_name）
    private String hisCode;            // 院内编码
    private String fieldChanges;       // 变更字段JSON
    private String reviewStatus;       // 审批状态: PENDING/APPROVED/REJECTED
    private String reviewStatusLabel;  // 审批状态中文
    private String reviewerName;       // 审批人
    private String reviewComment;      // 审批意见

    private String changeType;
    private String changeTypeLabel;
    private LocalDate oldExpireDate;
    private LocalDate newExpireDate;
    private String operatorName;
    private String note;
    private LocalDateTime createdAt;
}
