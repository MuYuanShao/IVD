package com.zlth.ivd.catalog.service;

import com.zlth.ivd.catalog.entity.FiveCodeMapping;

/**
 * 五码映射 Service
 *
 * @author IVD Team
 * @since 2026-07-03
 */
public interface FiveCodeMappingService {

    /**
     * 创建五码映射（试剂新增时自动调用）
     */
    FiveCodeMapping createMapping(Long reagentId, String hisCode, String udiDi,
                                  String gtin, String insuranceCode, String nmpaRegNo);

    /**
     * 按试剂ID查询
     */
    FiveCodeMapping getByReagentId(Long reagentId);

    /**
     * 更新五码映射（试剂编辑时自动调用）
     */
    void updateMapping(Long reagentId, String udiDi, String gtin,
                       String insuranceCode, String nmpaRegNo);

    /**
     * 按试剂ID硬删除
     */
    void deleteByReagentId(Long reagentId);
}
