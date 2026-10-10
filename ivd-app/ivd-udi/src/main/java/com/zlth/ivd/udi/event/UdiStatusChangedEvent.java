package com.zlth.ivd.udi.event;

import com.zlth.ivd.common.enums.UdiStatusEnum;

public record UdiStatusChangedEvent(Long batchId, String batchNo, UdiStatusEnum from,
                                    UdiStatusEnum to, String operator) {
}
