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
    @org.springframework.beans.factory.annotation.Value("${ivd.isolation.enabled:false}")
    private boolean isolationEnabled;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(OperationAuditEntry entry) {
        persist(entry,false);
    }

    /** Domain evidence must commit or roll back together with its business mutation. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void appendDomain(OperationAuditEntry entry) {
        if (!Boolean.TRUE.equals(entry.getSuccess())) throw new IllegalArgumentException("领域成功审计不能记录失败请求");
        persist(entry,true);
    }

    private void persist(OperationAuditEntry entry,boolean required) {
        OperationAuditLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            if (isolationEnabled || required) throw new IllegalStateException("审计持久化未配置，操作已拒绝");
            log.debug("当前应用上下文未启用操作审计 Mapper，跳过持久化");
            return;
        }
        if(isolationEnabled) {
            entry.setInstitutionId(com.zlth.ivd.common.security.InstitutionContext.current().map(s->s.institutionId()).orElse(null));
            mapper.appendScoped(entry);
        } else mapper.append(entry);
    }
}
