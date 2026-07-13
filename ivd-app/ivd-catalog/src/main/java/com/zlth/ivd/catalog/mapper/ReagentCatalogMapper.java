package com.zlth.ivd.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 试剂目录 Mapper
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Mapper
public interface ReagentCatalogMapper extends BaseMapper<ReagentCatalog> {

    /**
     * 自定义分页查询（多条件组合）
     */
    IPage<ReagentCatalog> pageList(Page<ReagentCatalog> page, @Param("query") Object query);

    /**
     * 按审批状态查询待办列表
     */
    // List<ReagentCatalog> findByStatus(@Param("status") String status, @Param("limit") int limit);
}
