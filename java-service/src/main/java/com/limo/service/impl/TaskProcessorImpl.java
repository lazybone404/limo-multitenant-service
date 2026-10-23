package com.limo.service.impl;

import com.limo.client.PythonCoreClient;
import com.limo.service.TaskProcessResult;
import com.limo.service.TaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务处理器实现（默认）：调 python-core。
 * 进阶口：将来换 MQ 时，加一个 TaskProcessor 的 MQ 实现即可（本类可保留用于兜底）。
 */
@Component
@RequiredArgsConstructor
public class TaskProcessorImpl implements TaskProcessor {

    private final PythonCoreClient pythonCoreClient;

    @Override
    public TaskProcessResult process(Long userId, Long taskId, String type, String url, String text, String style, Map<String, Object> options) {
        Map<String, Object> r = pythonCoreClient.convert(type, url, text, style, options);
        String rawText = (String) r.getOrDefault("raw_text", "");
        String note = (String) r.getOrDefault("note", "");
        Integer dur = r.get("duration") instanceof Number n ? n.intValue() : 0;
        // 核心自报消耗（黑盒：token 由核心算，java 不猜）
        int tokens = 0;
        if (r.get("usage") instanceof Map<?, ?> u) {
            Object tk = u.get("tokens");
            tokens = tk instanceof Number n ? n.intValue() : 0;
        }
        return new TaskProcessResult(rawText, dur, note, tokens);
    }

    @Override
    public TaskProcessResult processFile(Long userId, Long taskId, String type, String filePath) {
        Map<String, Object> r = pythonCoreClient.convertFile(type, filePath);
        String rawText = (String) r.getOrDefault("raw_text", "");
        String note = (String) r.getOrDefault("note", "");
        Integer dur = r.get("duration") instanceof Number n ? n.intValue() : 0;
        int tokens = 0;
        if (r.get("usage") instanceof Map<?, ?> u) {
            Object tk = u.get("tokens");
            tokens = tk instanceof Number n ? n.intValue() : 0;
        }
        return new TaskProcessResult(rawText, dur, note, tokens);
    }
}
