package com.zlth.ivd.common.mapper;

import com.zlth.ivd.common.audit.OperationAuditEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditLogMapper {

    @Insert("""
            INSERT INTO operation_audit_log
              (operator_id, operator_name, module, action, request_method, request_path,
               handler, client_ip, reason, success, error_message, duration_ms, occurred_at)
            VALUES
              (#{operatorId}, #{operatorName}, #{module}, #{action}, #{requestMethod}, #{requestPath},
               #{handler}, #{clientIp}, #{reason}, #{success}, #{errorMessage}, #{durationMs}, #{occurredAt})
            """)
    int append(OperationAuditEntry entry);
}
