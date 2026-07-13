package com.zlth.ivd.catalog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zlth.ivd.catalog.entity.UdiBacklog;
import com.zlth.ivd.common.enums.MaterialTypeEnum;

/**
 * UDI 待补全 Service
 *
 * @author IVD Team
 * @since 2026-07-03
 */
public interface UdiBacklogService {

    /**
     * 分页查询 UDI 待补全列表
     */
    IPage<UdiBacklog> pageList(int page, int pageSize, String impactLevel);

    /**
     * 创建待补全记录
     */
    void createBacklog(Long reagentId, MaterialTypeEnum materialType, String impactLevel);

    /**
     * 删除待补全记录（试剂 UDI 补全后）
     */
    void deleteByReagentId(Long reagentId);
}
