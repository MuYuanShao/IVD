package com.zlth.ivd.inventory.service;

import java.util.List;
import java.util.Map;

public interface InstrumentUsageService {
    List<Map<String,Object>> devices();
    List<Map<String,Object>> activeLoads();
    List<Map<String,Object>> availableBatches(String keyword);
    void bind(Long batchId,Long deviceId);
    void unload(Long loadId);
}
