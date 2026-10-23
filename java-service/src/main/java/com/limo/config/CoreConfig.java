package com.limo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 线程池配置：慢任务（调 python 核心）放后台线程执行，主流程立即返回。
 * 进阶口：将来做削峰/消息队列，把这个换掉即可（见 TaskProcessor 抽象）。
 */
@Configuration
public class CoreConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("task-");
        ex.initialize();
        return ex;
    }
}
