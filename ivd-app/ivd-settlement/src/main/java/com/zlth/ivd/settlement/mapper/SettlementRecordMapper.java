package com.zlth.ivd.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.settlement.entity.SettlementRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SettlementRecordMapper extends BaseMapper<SettlementRecord> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM settlement_record WHERE id=#{id} FOR UPDATE")
    SettlementRecord lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
