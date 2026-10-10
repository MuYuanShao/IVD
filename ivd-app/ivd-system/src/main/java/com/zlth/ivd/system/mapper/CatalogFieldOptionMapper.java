package com.zlth.ivd.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.system.entity.CatalogFieldOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CatalogFieldOptionMapper extends BaseMapper<CatalogFieldOption> {

    @Delete("DELETE FROM catalog_field_option WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
