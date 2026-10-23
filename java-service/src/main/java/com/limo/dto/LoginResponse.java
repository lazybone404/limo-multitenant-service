package com.limo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 登录成功返回：token + 用户信息 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private Integer points;
    private String planCode;
}
