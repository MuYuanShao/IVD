package com.zlth.ivd.common.aspect;

import com.zlth.ivd.common.annotation.AuditLog;
import com.zlth.ivd.common.audit.OperationAuditEntry;
import com.zlth.ivd.common.audit.OperationAuditLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
@RequiredArgsConstructor
public class AuditLogAspect {

    private final OperationAuditLogWriter writer;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        AuditLog auditLog = AnnotatedElementUtils.findMergedAnnotation(
                AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass), AuditLog.class);
        // Who — 操作人
        String operator = getCurrentUsername();
        // When — 开始时间
        LocalDateTime startTime = LocalDateTime.now();
        // Where — 模块
        String module = auditLog != null ? auditLog.module() : targetClass.getSimpleName();
        // What — 操作类型
        HttpServletRequest request = currentRequest();
        String action = auditLog != null ? auditLog.action()
                : request != null ? request.getMethod() : signature.getMethod().getName();
        // 方法签名
        String method = joinPoint.getSignature().toShortString();

        log.info("[审计] 操作人={} | 模块={} | 操作={} | 方法={} | 时间={} | 状态=开始",
                operator, module, action, method, startTime);

        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable e) {
            errorMessage = truncate(e.getMessage(), 1000);
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            OperationAuditEntry entry = OperationAuditEntry.builder()
                    .operatorId(getCurrentUserId())
                    .operatorName(operator)
                    .module(module)
                    .action(action)
                    .requestMethod(request != null ? request.getMethod() : null)
                    .requestPath(request != null ? request.getRequestURI() : null)
                    .handler(method)
                    .clientIp(request != null ? resolveClientIp(request) : null)
                    .reason(auditLog != null ? truncate(auditLog.description(), 500) : null)
                    .success(success)
                    .errorMessage(errorMessage)
                    .durationMs(elapsed)
                    .occurredAt(startTime)
                    .build();
            try {
                writer.append(entry);
            } catch (Exception auditError) {
                log.error("审计日志持久化失败: handler={}, error={}", method, auditError.getMessage());
            }
            log.info("[审计] 操作人={} | 模块={} | 操作={} | 方法={} | 耗时={}ms | 状态={}",
                    operator, module, action, method, elapsed, success ? "成功" : "失败");
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Long id ? id : null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",", 2)[0].trim() : request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof Long && auth.getDetails() instanceof String) {
            return auth.getDetails().toString();
        }
        return auth.getName();
    }
}
