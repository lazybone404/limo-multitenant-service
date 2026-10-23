package com.limo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 转换任务表：用户每次"内容转笔记"一条记录。
 * 状态机 PENDING → PROCESSING → DONE / FAILED（体现慢任务异步化）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** video / document */
    @Column(nullable = false, length = 10)
    private String type;

    /** 来源（视频 url / 文件信息 / 描述） */
    @Column(name = "source_ref", length = 500)
    private String source;

    /** 状态：PENDING / PROCESSING / DONE / FAILED（常量见 common.TaskStatus） */
    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    /**
     * 失败原因：NO_POINTS / CORE_DOWN / BAD_INPUT / UNKNOWN（仅 FAILED 时有值）。
     * 原先所有失败都落成同一个 FAILED：余额不足、python 挂了、文件读不了，
     * 用户和排查的人都看不出该做什么。加上这一列之后，前端能按原因给不同的下一步。
     */
    @Column(length = 20)
    private String failReason;

    /** 生成的笔记（DONE 后填充） */
    @Column(columnDefinition = "TEXT")
    private String resultText;

    /** 本次消耗的 token（视频按时长、文档按文本量核算） */
    private Integer tokens = 0;

    /** 本次消耗的积分 */
    private Integer points = 0;

    /** 视频时长（秒），文档则 0 */
    private Integer durationSec = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 关联的来源 id（一次转换的来源） */
    @Column
    private Long sourceId;

    /**
     * 去重键：同一用户 + 同一来源 + 同一类型 的 sha256。
     *
     * 为什么要落到数据库上：原先只在提交前「先查有没有在途任务，没有就建」——
     * 十个请求同时进来，十个都查到「没有」，于是建出十个任务、扣十次积分。
     * 查和建之间那个空档，只有唯一索引能堵住。
     *
     * 为什么终态要置 NULL：去重的窗口只应该在「处理中」。任务一旦 DONE 或 FAILED，
     * 用户对同一份来源再提交一次是完全合理的（换个风格重跑、上次失败了重试）。
     * MySQL 和 H2 的唯一索引都允许多个 NULL，置空就把唯一约束让开了。
     */
    @Column(name = "dedup_key", length = 64, unique = true)
    private String dedupKey;

    private LocalDateTime finishedAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
