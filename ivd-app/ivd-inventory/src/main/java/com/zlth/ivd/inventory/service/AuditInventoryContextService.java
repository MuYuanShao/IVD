package com.zlth.ivd.inventory.service;

import java.time.LocalDateTime;
import java.util.Map;

/** Server-owned facts used by the batch-aware audit pipeline. */
public interface AuditInventoryContextService {
    Map<String, Object> resolve(String device, LocalDateTime at, String batchNo);
}
