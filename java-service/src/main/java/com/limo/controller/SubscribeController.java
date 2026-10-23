package com.limo.controller;

import com.limo.common.ApiResponse;
import com.limo.dto.SubscribeRequest;
import com.limo.dto.SubscribeResponse;
import com.limo.service.SubscribeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/** 订阅接口（需登录）：POST /api/subscribe  body {planCode} */
@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;

    @PostMapping
    public ApiResponse<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest req) {
        return ApiResponse.ok(subscribeService.subscribe(req));
    }
}
