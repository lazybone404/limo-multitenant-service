package com.limo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * limo Java 业务端启动类。
 *
 * @EnableAsync      开启异步任务（慢任务：调 python 核心在后台线程执行，主流程立即返回）
 * @EnableScheduling 开启定时任务，当前只有 TaskRecovery 一个用途：
 *                   回收卡死在 PROCESSING 的任务，否则那个来源会被去重键永久占住。
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class LimoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LimoApplication.class, args);
    }
}
