package com.limo.repository;

import com.limo.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 笔记资产持久层 */
public interface NoteRepository extends JpaRepository<Note, Long> {

    /** 配额校验用：用户已有笔记数（含版本） */
    long countByUserId(Long userId);

    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 列表分页用：按创建时间倒序取一页（原先一次性把全部笔记查出来返回） */
    List<Note> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);

    List<Note> findByUserIdAndSourceIdOrderByVersionDesc(Long userId, Long sourceId);

    /** 详情/删除用：校验归属（数据隔离） */
    Optional<Note> findByIdAndUserId(Long id, Long userId);

    /**
     * 下一个版本号 = 当前最大版本 + 1，由数据库算。
     * 原先是把整个版本列表查出来取第一条 + 1，并发下两个请求会算出同一个号；
     * 现在由数据库聚合，外面再靠 note 表上的 (userId, sourceId, version) 唯一约束兜底。
     */
    @Query("select coalesce(max(n.version), 0) from Note n where n.userId = :userId and n.sourceId = :sourceId")
    int maxVersion(@Param("userId") Long userId, @Param("sourceId") Long sourceId);
}
