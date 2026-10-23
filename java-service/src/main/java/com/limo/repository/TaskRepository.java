package com.limo.repository;

import com.limo.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 转换任务持久层 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 幂等去重：同一用户、同一来源、还在处理中 → 不重复创建（防重复扣积分/重复调 python） */
    Optional<Task> findFirstByUserIdAndSourceAndStatusIn(Long userId, String source, List<String> status);

    /**
     * 按去重键查在途任务。dedupKey 上有唯一索引，所以这里最多命中一条。
     * 用它取代上面的「按来源 + 状态查」：那一版是读后写，查询和插入之间能被并发插队。
     */
    Optional<Task> findFirstByDedupKey(String dedupKey);

    /**
     * 认领任务：只有当前状态还是 from，才允许改成 to。返回受影响行数。
     *
     * 这就是「条件更新」——判断和写入在同一条 SQL 里由数据库原子完成。
     * 原先的写法是先 findById 读出来、改字段、再 save，两个线程能同时把
     * PENDING 改成 PROCESSING，同一个任务被处理两遍、扣两次积分。
     */
    @Modifying
    @Transactional
    @Query("update Task t set t.status = :to where t.id = :id and t.status = :from")
    int compareAndSetStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);
}
