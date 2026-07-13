package com.zlth.ivd.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一响应体包装
 * <p>
 * 格式：{"code":200, "message":"success", "data":{}, "timestamp":"..."}
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String detail;
    private LocalDateTime timestamp;

    private Result() {
        this.timestamp = LocalDateTime.now();
    }

    // ==================== 成功 ====================

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    // ==================== 失败 ====================

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> Result<T> error(int code, String message, String detail) {
        Result<T> r = error(code, message);
        r.detail = detail;
        return r;
    }

    public static <T> Result<T> badRequest(String message) {
        return error(40001, message);
    }

    public static <T> Result<T> unauthorized() {
        return error(40100, "未认证，请先登录");
    }

    public static <T> Result<T> forbidden() {
        return error(40300, "无权限访问");
    }

    public static <T> Result<T> notFound(String message) {
        return error(40400, message);
    }

    public static <T> Result<T> conflict(String message) {
        return error(40900, message);
    }

    public static <T> Result<T> serverError(String message) {
        return error(50000, message);
    }
}
