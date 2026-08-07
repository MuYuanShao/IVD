package com.zlth.ivd.udi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlth.ivd.udi.entity.UdiUniqueCodeSeq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UdiUniqueCodeSeqMapper extends BaseMapper<UdiUniqueCodeSeq> {

    /**
     * 原子自增日序列号，返回自增后的值
     * INSERT ... ON DUPLICATE KEY UPDATE current_seq = current_seq + #{count}
     */
    @Update("INSERT INTO udi_unique_code_seq (date_key, current_seq) VALUES (#{dateKey}, #{count}) "
            + "ON DUPLICATE KEY UPDATE current_seq = current_seq + #{count}")
    int incrementSeq(@Param("dateKey") String dateKey, @Param("count") int count);
}
