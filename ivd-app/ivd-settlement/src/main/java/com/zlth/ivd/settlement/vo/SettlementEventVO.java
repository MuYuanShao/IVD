package com.zlth.ivd.settlement.vo;
import java.time.LocalDateTime;
public record SettlementEventVO(Long id,String fromStatus,String toStatus,String reason,Long operatorId,
        String operatorName,LocalDateTime createdAt) {}
