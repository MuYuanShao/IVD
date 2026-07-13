package com.zlth.ivd.common.exception;

/**
 * UDI 缺失异常
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class UdiMissingException extends BizException {

    public UdiMissingException(Long reagentId, String hisCode) {
        super(40004,
                String.format("物料 [%s] (ID=%d) 缺少 UDI-DI，无法执行扫码验收", hisCode, reagentId),
                String.format("reagentId=%d, hisCode=%s", reagentId, hisCode));
    }
}
