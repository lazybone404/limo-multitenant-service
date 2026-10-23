package com.limo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

/** 提交转笔记任务请求：type=video/document；text 直接文本，或 url（文档/视频链接） */
@Data
public class TaskRequest {
    @NotBlank(message = "类型不能为空")
    private String type;   // video / document
    private String url;
    private String text;

    /** 风格（文档/视频有各自风格集，如 detailed/outline/meeting）——按类型定制化 */
    private String style;
    /** 生成选项，如 {toc:true, summary:true} */
    private Map<String, Object> options;
}
