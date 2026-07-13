package com.zlth.ivd.common.exception;

/**
 * 证照过期异常
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class LicenseExpiredException extends BizException {

    public LicenseExpiredException(String certNo, String supplierName) {
        super(40003,
                String.format("供应商 [%s] 的证照 [%s] 已过期，采购流程已冻结", supplierName, certNo),
                String.format("certNo=%s, supplier=%s", certNo, supplierName));
    }
}
