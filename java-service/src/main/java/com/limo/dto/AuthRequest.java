package com.limo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录 / 注册 请求 */
@Data
public class AuthRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
