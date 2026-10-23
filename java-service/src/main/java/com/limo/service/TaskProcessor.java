package com.limo.service;

import java.util.Map;

/**
 * 任务处理器抽象（留进阶口子）：
 * 现在是【调 python-core 同步处理】实现；将来换【消息队列】只加一个 MQ 实现即可，结构不动。
 */
public interface TaskProcessor {
    TaskProcessResult process(Long userId, Long taskId, String type, String url, String text, String style, Map<String, Object> options);

    TaskProcessResult processFile(Long userId, Long taskId, String type, String filePath);
}
