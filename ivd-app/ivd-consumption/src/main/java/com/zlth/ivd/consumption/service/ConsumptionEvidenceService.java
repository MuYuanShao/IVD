package com.zlth.ivd.consumption.service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public interface ConsumptionEvidenceService {
    ConsumptionEvidence requireEvidence(Long id,Long batchId,Long deviceId,LocalDateTime testedAt);
    java.util.List<ConsumptionEvidence> options(Long batchId,Long deviceId,LocalDateTime testedAt);
    record ConsumptionEvidence(Long id,Long batchId,Long reagentId,Long deviceId,Long warehouseId,
            BigDecimal quantity,String unit,BigDecimal amount,LocalDateTime occurredAt) { }
}
