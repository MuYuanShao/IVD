package com.zlth.ivd.common.exception;

import lombok.Getter;

/**
 * 业务异常基类 — 所有业务异常继承此类
 * <p>
 * 错误码对应规范 §7.3: 40001-40099 业务校验 / 40100 未认证 / 40300 无权限 / 40400 资源不存在 / 40900 数据冲突
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Getter
public class BizException extends RuntimeException {

    private final int errorCode;
    private final String detail;

    public BizException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BizException(int errorCode, String message, String detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public BizException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detail = null;
    }
}
