package com.zlth.ivd.common.exception;

import java.math.BigDecimal;

/**
 * 库存不足异常
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class InsufficientStockException extends BizException {

    public InsufficientStockException(Long reagentId, BigDecimal required, BigDecimal available) {
        super(40002,
                String.format("试剂 ID=%d 库存不足：需要 %s，可用 %s", reagentId, required.toPlainString(), available.toPlainString()),
                String.format("reagentId=%d, required=%s, available=%s", reagentId, required, available));
    }
}
