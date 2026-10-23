package com.limo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回体（国内企业规范）。所有接口都返回 {code, message, data}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(200, "success", data); }
    public static <T> ApiResponse<T> ok(String msg, T data) { return new ApiResponse<>(200, msg, data); }
    public static <T> ApiResponse<T> error(int code, String msg) { return new ApiResponse<>(code, msg, null); }
}
