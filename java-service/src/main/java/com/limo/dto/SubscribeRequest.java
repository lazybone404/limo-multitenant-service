package com.limo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 订阅请求：传套餐码（free / monthly / monthly_auto / yearly / yearly_auto） */
@Data
public class SubscribeRequest {
    @NotBlank(message = "套餐不能为空")
    private String planCode;
}
