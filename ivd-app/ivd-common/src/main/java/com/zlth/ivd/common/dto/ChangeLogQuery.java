package com.zlth.ivd.common.dto;

import lombok.Data;

/**
 * 统一变更记录查询请求 — 支持证照管理 + 试剂目录
 *
 * @author IVD Team
 * @since 2026-07-21
 */
@Data
public class ChangeLogQuery {

    private String module;       // 模块筛选：LICENSE(证照) / CATALOG(试剂目录) / 空(全部)
    private String changeType;   // 变更类型筛选
    private String startDate;    // 开始日期 yyyy-MM-dd
    private String endDate;      // 结束日期 yyyy-MM-dd
    private String keyword;      // 模糊搜索：供应商名称/证号（证照）或 试剂名称/院内编码（试剂）
    private Integer page = 1;
    private Integer pageSize = 20;
}
