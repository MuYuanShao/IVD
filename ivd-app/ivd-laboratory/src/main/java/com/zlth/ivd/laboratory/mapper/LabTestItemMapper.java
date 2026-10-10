package com.zlth.ivd.laboratory.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.laboratory.entity.LabTestItem;
import org.apache.ibatis.annotations.*;
@Mapper
public interface LabTestItemMapper extends BaseMapper<LabTestItem> {
    @Select("SELECT * FROM lab_test_item WHERE id=#{id} AND institution_id=#{institutionId} FOR UPDATE")
    LabTestItem lockOwned(@Param("id") Long id,@Param("institutionId") long institutionId);
}
