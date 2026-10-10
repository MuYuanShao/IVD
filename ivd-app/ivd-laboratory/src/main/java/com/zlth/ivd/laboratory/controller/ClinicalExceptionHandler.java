package com.zlth.ivd.laboratory.controller;

import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.exception.BizException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** Never log raw clinical payloads or parser messages which may contain patient information. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PatientController.class)
public class ClinicalExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> invalidRequest(Exception ignored){return Result.badRequest("临床请求格式或字段无效，请核对必填项与日期格式");}
    @ExceptionHandler(BizException.class)
    public Result<?> business(BizException error){return Result.error(error.getErrorCode(),error.getMessage());}
    @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> unknown(Exception ignored){return Result.serverError("临床资料处理失败，请联系管理员；未返回敏感详情");}
}
