package com.zlth.ivd.udi.mapper;

import com.zlth.ivd.udi.entity.UdiStatusLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UdiStatusLogMapper {
    @Insert("""
            INSERT INTO udi_status_log
              (id, batch_no, from_status, to_status, operator_id, operator_name, remark, occurred_at, created_at)
            VALUES
              (#{id}, #{batchNo}, #{fromStatus}, #{toStatus}, #{operatorId}, #{operatorName},
               #{remark}, #{occurredAt}, #{createdAt})
            """)
    int append(UdiStatusLog statusLog);
}
