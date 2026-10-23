package com.limo.client;

import com.limo.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * python-core 客户端：Java 调 python 核心（/convert）。
 * 做【超时 + 重试 + 降级】——外部依赖稳定性考量（python 挂了不拖垮主流程）。
 *
 * 2026-10-13 改了两点：
 *   1) 重试只对「重试有意义」的失败做。原先是一个 for 循环无差别试两次，
 *      连 4xx（我们送过去的输入有问题）也重试一遍，只是让用户白等一个读超时。
 *   2) 加日志。原先失败只剩一句「核心处理服务暂不可用」，是哪一步、什么原因全丢了。
 */
@Slf4j
@Component
public class PythonCoreClient {

    /** 最多调用的次数（= 首次 + 1 次重试） */
    private static final int MAX_ATTEMPTS = 2;

    @Value("${limo.python-core.base-url:http://localhost:8001}")
    private String baseUrl;

    private final RestTemplate rest;

    public PythonCoreClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);        // 连接超时
        f.setReadTimeout(120000);         // 读取超时（视频转写可能久）
        this.rest = new RestTemplate(f);
    }

    /** 调 python-core /convert（JSON），返回 {note, raw_text, duration, usage} */
    public Map<String, Object> convert(String type, String url, String text, String style, Map<String, Object> options) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("url", url == null ? "" : url);
        body.put("text", text == null ? "" : text);
        if (style != null) body.put("style", style);
        if (options != null) body.put("options", options);
        return post("/convert", body, "text");
    }

    /** 调 python-core /convert（multipart 上传文件），返回 {note, raw_text, duration, usage} */
    public Map<String, Object> convertFile(String type, String filePath) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("type", type);
        body.add("file", new FileSystemResource(filePath));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return post("/convert", new HttpEntity<>(body, h), "file");
    }

    /**
     * 统一的 POST + 重试 + 降级。
     *
     * 4xx 直接放弃：那是我们送过去的东西有问题（参数、格式），重试一次结果一样。
     * 5xx 和网络异常才重试 —— 那类失败下一次有可能好。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String kind) {
        String url = baseUrl + path;
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<Map> resp = rest.postForEntity(url, body, Map.class);
                return resp.getBody();
            } catch (HttpClientErrorException e) {
                log.warn("核心拒绝了 {} 请求（{}）：{}", kind, e.getStatusCode(), e.getResponseBodyAsString());
                throw new BizException(400, "核心无法处理这个内容：" + e.getStatusCode());
            } catch (Exception e) {
                last = e;
                log.warn("调用核心失败（{}，第 {}/{} 次）：{}", kind, attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        // 降级：python 挂了返回友好错误（不抛 500 给用户）
        throw new BizException(502, "核心处理服务暂不可用，请稍后再试：" + (last != null ? last.getMessage() : ""));
    }
}
