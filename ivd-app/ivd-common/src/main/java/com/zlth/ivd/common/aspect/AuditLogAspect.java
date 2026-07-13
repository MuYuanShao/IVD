package com.zlth.ivd.common.aspect;

import com.zlth.ivd.common.annotation.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志切面 — 记录 5W 操作日志
 * <p>
 * 一期输出到日志文件，后续可写入 audit_log 表或发送到 ELK
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // Who — 操作人
        String operator = getCurrentUsername();
        // When — 开始时间
        LocalDateTime startTime = LocalDateTime.now();
        // Where — 模块
        String module = auditLog.module();
        // What — 操作类型
        String action = auditLog.action();
        // 方法签名
        String method = joinPoint.getSignature().toShortString();

        log.info("[审计] 操作人={} | 模块={} | 操作={} | 方法={} | 时间={} | 状态=开始",
                operator, module, action, method, startTime);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[审计] 操作人={} | 模块={} | 操作={} | 方法={} | 耗时={}ms | 状态=成功",
                    operator, module, action, method, elapsed);
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[审计] 操作人={} | 模块={} | 操作={} | 方法={} | 耗时={}ms | 状态=失败 | 原因={}",
                    operator, module, action, method, elapsed, e.getMessage());
            throw e;
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() != null) {
            return auth.getDetails().toString();
        }
        return "anonymous";
    }
}
