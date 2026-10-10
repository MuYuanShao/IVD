package com.zlth.ivd.common.audit;

import com.zlth.ivd.common.mapper.OperationAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationAuditLogWriter {

    private final ObjectProvider<OperationAuditLogMapper> mapperProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(OperationAuditEntry entry) {
        OperationAuditLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            log.debug("当前应用上下文未启用操作审计 Mapper，跳过持久化");
            return;
        }
        mapper.append(entry);
    }
}
