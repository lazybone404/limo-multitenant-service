package com.limo.service;

/** 结果：python 核心处理后返回的信息（tokens=核心自报消耗，java 只用于记账） */
public record TaskProcessResult(String rawText, int durationSec, String note, int tokens) {
}
