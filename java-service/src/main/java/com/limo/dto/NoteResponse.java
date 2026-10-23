package com.limo.dto;

import java.time.LocalDateTime;

/** 笔记响应（列表/详情） */
public record NoteResponse(Long id, Long sourceId, Integer version, String title, String preview,
                           String content, String type, Integer tokens, Integer points, LocalDateTime createdAt) {
}
