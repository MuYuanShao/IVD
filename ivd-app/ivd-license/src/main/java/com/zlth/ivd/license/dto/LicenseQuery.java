package com.zlth.ivd.license.dto;

import lombok.Data;

/**
 * 证照查询请求
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
public class LicenseQuery {

    private Long reagentId;      // 关联试剂 ID
    private String keyword;       // 搜索：供应商名/证号
    private String licenseType;   // 证照类型筛选
    private String status;        // 状态筛选
    private Integer daysToExpire; // 距到期天数（效期预警）
    private Integer page = 1;
    private Integer pageSize = 10;
}
