package com.limo.common;

/**
 * 业务异常：服务端用抛出它表达"可预期的业务失败"（如积分不足、未登录），
 * 由 GlobalExceptionHandler 统一转成 JSON。
 */
public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String message) { super(message); this.code = code; }
    public BizException(String message) { this(400, message); }
    public int getCode() { return code; }
}
