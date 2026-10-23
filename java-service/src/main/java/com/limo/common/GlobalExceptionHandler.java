package com.limo.common;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：把所有异常统一转成 {code, message, data}，不用到处 try-catch。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 可预期的业务异常（如积分不足、未登录） */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> biz(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败 @Valid */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> valid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage).orElse("参数错误");
        return ApiResponse.error(400, msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> type(MethodArgumentTypeMismatchException e) {
        return ApiResponse.error(400, "参数类型错误");
    }

    /** 兜底：未预期异常（500，打印完整堆栈方便排查） */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> other(Exception e) {
        e.printStackTrace();
        return ApiResponse.error(500, "服务器内部错误: " + e.getMessage());
    }
}
