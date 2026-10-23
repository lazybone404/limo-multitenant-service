package com.limo.controller;

import com.limo.common.ApiResponse;
import com.limo.dto.AuthRequest;
import com.limo.dto.LoginResponse;
import com.limo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：注册 / 登录 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody AuthRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody AuthRequest req) {
        return ApiResponse.ok(authService.login(req));
    }
}
