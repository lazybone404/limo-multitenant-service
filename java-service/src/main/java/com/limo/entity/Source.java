package com.limo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 来源表（对 C 端 transcript 简化）：一次内容输入的标识（url / 文件 / 文本）。
 * 一个用户的一个来源可多次生成笔记（note），对应 C 端"来源↔产物"分离。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "src")
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** video / document */
    @Column(nullable = false, length = 10)
    private String sourceType;

    /** 来源标识：视频 url / 文件名 / 文本描述 */
    @Column(length = 500)
    private String origin;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
