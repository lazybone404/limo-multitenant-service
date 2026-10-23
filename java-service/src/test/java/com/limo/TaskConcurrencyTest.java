package com.limo;

import com.limo.common.TaskStatus;
import com.limo.entity.Note;
import com.limo.entity.Task;
import com.limo.repository.NoteRepository;
import com.limo.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 并发正确性的测试。
 *
 * 这两条原先都没有测试，而它们恰好是系统对外承诺的能力：
 *   - 「并发扣费走条件更新防超扣」→ 任务的认领也得是条件更新
 *   - 版本号在并发下不能撞车
 *
 * 原先的实现是先 findById 读出来、setStatus、再 save，两个线程能同时把
 * PENDING 改成 PROCESSING，同一个来源被处理两遍、扣两次积分。
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskConcurrencyTest {

    @Autowired TaskRepository taskRepo;
    @Autowired NoteRepository noteRepo;

    @Test
    void 条件更新认领_同一个任务第二次认领会失败() {
        Task t = taskRepo.save(Task.builder().userId(7001L).type("document")
                .source("claim-once").sourceId(7001L).status(TaskStatus.PENDING).build());

        assertThat(taskRepo.compareAndSetStatus(t.getId(), TaskStatus.PENDING, TaskStatus.PROCESSING,
                        LocalDateTime.now()))
                .as("第一次认领应当成功").isEqualTo(1);
        assertThat(taskRepo.compareAndSetStatus(t.getId(), TaskStatus.PENDING, TaskStatus.PROCESSING,
                        LocalDateTime.now()))
                .as("状态已经不是 PENDING，第二次必须返回 0").isZero();
    }

    @Test
    void 并发认领_八个线程只有一个能拿到任务() throws Exception {
        Task t = taskRepo.save(Task.builder().userId(7002L).type("document")
                .source("claim-race").sourceId(7002L).status(TaskStatus.PENDING).build());

        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger claimed = new AtomicInteger();

        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (taskRepo.compareAndSetStatus(t.getId(), TaskStatus.PENDING,
                            TaskStatus.PROCESSING, LocalDateTime.now()) == 1) {
                        claimed.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 并发写同一行时数据库可能报锁等待，那也算「没抢到」
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).as("八个线程都应在 30 秒内结束").isTrue();
        pool.shutdownNow();

        assertThat(claimed.get()).as("只能有一个认领成功").isEqualTo(1);
        assertThat(taskRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void 版本号_由数据库聚合出下一个_不会跳号也不会撞车() {
        Long uid = 7003L;
        Long sid = 7003L;

        assertThat(noteRepo.maxVersion(uid, sid)).as("一条都没有时是 0").isZero();

        noteRepo.save(Note.builder().userId(uid).sourceId(sid).version(1).content("v1").build());
        assertThat(noteRepo.maxVersion(uid, sid)).isEqualTo(1);

        noteRepo.save(Note.builder().userId(uid).sourceId(sid).version(2).content("v2").build());
        assertThat(noteRepo.maxVersion(uid, sid)).isEqualTo(2);

        assertThat(noteRepo.maxVersion(uid, 999999L)).as("别的来源互不影响").isZero();
    }

    @Test
    void 唯一约束_同一来源同一版本号插不进去() {
        Long uid = 7004L;
        Long sid = 7004L;

        noteRepo.saveAndFlush(Note.builder().userId(uid).sourceId(sid).version(1).content("a").build());

        // 这条约束是最后一道防线：就算上面的 maxVersion 在极端并发下算重了，
        // 数据库也会拒绝第二条，而不是让同一个版本号出现两份内容不同的笔记。
        assertThatThrownBy(() -> noteRepo.saveAndFlush(
                Note.builder().userId(uid).sourceId(sid).version(1).content("b").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
