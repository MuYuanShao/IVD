package com.zlth.ivd.system.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室试剂目录 VO — M00-2 扩展
 *
 * @author IVD Team
 * @since 2026-07-15
 */
@Data
public class DeptReagentVO {

    /** dept_reagent 关联ID */
    private Long id;

    /** 试剂ID */
    private Long reagentId;

    /** HIS编码 */
    private String hisCode;

    /** 物料名称 */
    private String genericName;

    /** 物料类型（枚举 code） */
    private String materialType;

    /** 物料类型（中文） */
    private String materialTypeLabel;

    /** 规格 */
    private String specification;

    /** 生产厂家 */
    private String manufacturer;

    /** 单位 */
    private String unit;

    /** 检测组别 */
    private String testGroups;

    /** 注册证号 */
    private String registrationCertNo;

    /** 关联时间 */
    private LocalDateTime createdAt;
}
