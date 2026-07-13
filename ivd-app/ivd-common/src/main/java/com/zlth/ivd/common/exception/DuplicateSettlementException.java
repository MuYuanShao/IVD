package com.zlth.ivd.common.exception;

/**
 * 重复结算异常 (HTTP 409)
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class DuplicateSettlementException extends BizException {

    public DuplicateSettlementException(String reqNo) {
        super(40900,
                String.format("申领单 [%s] 已结算，不可重复结算", reqNo),
                String.format("settlement already exists for reqNo=%s", reqNo));
    }
}
