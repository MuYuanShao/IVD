package com.zlth.ivd.common.config;

import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理 — 统一返回 Result<T> JSON 格式
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}, detail={}", e.getErrorCode(), e.getMessage(), e.getDetail());
        return Result.error(e.getErrorCode(), e.getMessage(), e.getDetail());
    }

    /** 参数校验失败 (@Valid on RequestBody) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.badRequest(msg);
    }

    /** 参数绑定失败 (@Valid on Query Params) */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return Result.badRequest(msg);
    }

    /** 约束校验失败 (Service 层 @Validated) */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        return Result.badRequest(e.getMessage());
    }

    /** 必填请求参数缺失（如 @RequestParam 未加 required=false 而调用方未传值） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getMessage());
        return Result.badRequest("缺少必填参数: " + e.getParameterName());
    }

    /** 请求参数类型不匹配（如路径变量/查询参数应为数字却传入非数字字符串） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return Result.badRequest("参数类型不正确: " + e.getName());
    }

    /** 路由未匹配（404） */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoHandlerFound(NoHandlerFoundException e) {
        return Result.notFound("接口不存在: " + e.getRequestURL());
    }

    /** 兜底：未预料的异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return Result.serverError("服务器内部错误，请联系管理员");
    }
}
