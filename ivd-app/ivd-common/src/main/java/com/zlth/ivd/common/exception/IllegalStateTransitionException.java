package com.zlth.ivd.common.exception;

/**
 * 非法状态转移异常
 * <p>
 * 当尝试执行不允许的状态转移时抛出（如从 CONSUMED 转移到 IN_STOCK）
 *
 * @author IVD Team
 * @since 2026-07-01
 */
public class IllegalStateTransitionException extends BizException {

    public IllegalStateTransitionException(String from, String to, String entityType) {
        super(40001,
                String.format("非法状态转移: %s 不允许从 [%s] 转移到 [%s]", entityType, from, to),
                String.format("entityType=%s, from=%s, to=%s", entityType, from, to));
    }

    public IllegalStateTransitionException(String message) {
        super(40001, message);
    }
}
