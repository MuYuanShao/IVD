package com.zlth.ivd.udi.service;

import com.zlth.ivd.common.enums.UdiStatusEnum;

/**
 * UDI 模块定义的库存状态端口；由库存模块实现，避免 UDI 反向依赖库存 Mapper。
 */
public interface UdiBatchStateStore {
    Long findUniqueBatchId(String batchNo);

    boolean updateStatus(Long batchId, UdiStatusEnum expectedStatus, UdiStatusEnum targetStatus);
}
