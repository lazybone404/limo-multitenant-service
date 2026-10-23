package com.limo.dto;

import lombok.Data;

/** 编辑笔记请求：title 可选、content（Markdown 原文，编辑后保存） */
@Data
public class UpdateNoteRequest {
    private String title;
    private String content;
}
