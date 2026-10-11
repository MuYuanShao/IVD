package com.zlth.ivd.inventory.service;
import java.time.LocalDateTime;
import java.math.BigDecimal;
public interface BatchEvidenceDirectoryService {
    BatchEvidence requireEvidence(Long batchId,Long deviceLoadId,Long deviceId,LocalDateTime testedAt);
    java.util.List<BatchEvidence> options(Long deviceId,LocalDateTime testedAt,String keyword);
    record BatchEvidence(Long batchId,String batchNo,Long reagentId,Long warehouseId,String unit,
            Long deviceLoadId,Long deviceId,LocalDateTime loadedAt,LocalDateTime unloadedAt) { }
}
