package com.zlth.ivd.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.inventory.entity.ReagentBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReagentBatchMapper extends BaseMapper<ReagentBatch> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM reagent_batch WHERE id=#{id} FOR UPDATE")
    ReagentBatch lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
