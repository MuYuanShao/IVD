package com.zlth.ivd.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.system.entity.ResearchProject;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ResearchProjectMapper extends BaseMapper<ResearchProject> {
    @Select("SELECT * FROM research_project WHERE id=#{id} FOR UPDATE")
    ResearchProject lockById(@Param("id") Long id);
}
