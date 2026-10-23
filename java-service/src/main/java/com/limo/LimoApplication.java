package com.limo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * limo Java 业务端启动类。
 * @EnableAsync：开启异步任务（慢任务：调 python 核心在后台线程执行，主流程立即返回）。
 */
@SpringBootApplication
@EnableAsync
public class LimoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LimoApplication.class, args);
    }
}
