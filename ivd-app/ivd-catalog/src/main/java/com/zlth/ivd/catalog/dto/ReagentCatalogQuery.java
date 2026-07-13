package com.zlth.ivd.catalog.dto;

import lombok.Data;

/**
 * 试剂目录分页查询参数
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Data
public class ReagentCatalogQuery {

    /** 院内编码（模糊） */
    private String hisCode;

    /** 物料名称（模糊） */
    private String genericName;

    /** UDI-DI（模糊） */
    private String udiDi;

    /** 注册证号（模糊） */
    private String registrationCertNo;

    /** 生产厂家（模糊） */
    private String manufacturer;

    /** 配送商（模糊） */
    private String distributor;

    /** 物料类型（精确匹配，传枚举 code） */
    private String materialType;

    /** 审批状态（精确匹配，传枚举 code） */
    private String approvalStatus;

    /** 适用组别（模糊） */
    private String testGroups;
}
