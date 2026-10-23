package com.limo.dto;

/** 订阅结果：当前套餐 + 剩余积分 + 笔记上限 */
public record SubscribeResponse(String planCode, String planName, Integer points, Integer noteLimit) {
}
