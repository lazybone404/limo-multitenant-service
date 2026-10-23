package com.limo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 笔记资产表（对 C 端 markdown view）：用户的核心产物，挂 source，可多版本。
 * 内容存 MySQL TEXT（Markdown），元数据独立列做列表/展示/成本回溯。
 * 配额（笔记上限）按此表记录数（count by userId）计算。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "note",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_note_user_source_version",
               columnNames = {"userId", "sourceId", "version"}))
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long sourceId;

    /** 同一来源下的版本号（默认 1，重生成递增） */
    @Column(nullable = false)
    private Integer version = 1;

    /** 笔记内容（Markdown，全文，TEXT） */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 200)
    private String title;

    /** 列表页预览（简短摘要） */
    @Column(length = 300)
    private String preview;

    @Column(length = 10)
    private String type;

    /** 该笔记消耗的 token / 积分 */
    private Integer tokens = 0;
    private Integer points = 0;
    private Integer durationSec = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
