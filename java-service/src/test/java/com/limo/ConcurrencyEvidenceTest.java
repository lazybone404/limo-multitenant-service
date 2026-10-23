package com.limo;

import com.limo.client.PythonCoreClient;
import com.limo.config.UserContext;
import com.limo.dto.TaskRequest;
import com.limo.entity.User;
import com.limo.repository.TaskRepository;
import com.limo.repository.UserRepository;
import com.limo.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 并发正确性的实测证据。
 *
 * 简历上写着「并发扣费走条件更新防超扣」「失败不扣」——这两句得有可复现的数字撑着，
 * 不能只是"我这么设计了"。这里把三个场景跑成数字。
 *
 * 注意：跑在 H2 内存库上（test profile），隔离级别与 MySQL 略有差别；
 * 条件更新这种「判断和写在同一条语句里」的写法，两种库上的语义是一致的。
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyEvidenceTest {

    @Autowired UserRepository userRepo;
    @Autowired TaskRepository taskRepo;
    @Autowired TaskService taskService;

    @MockitoBean PythonCoreClient pythonCoreClient;

    private Long newUser(int points) {
        return userRepo.save(User.builder()
                .username("ev_" + System.nanoTime())
                .password("x").points(points).planCode("free").status("ACTIVE")
                .build()).getId();
    }

    /** 起跑枪 + 等所有线程结束，避免快慢不一把并发测试跑成串行 */
    private int runConcurrently(int workers, int poolSize, RunnableWithIndex body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < workers; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (body.run(idx)) succeeded.incrementAndGet();
                } catch (Throwable e) {
                    // 并发写同一行时数据库可能报锁等待，算作「没抢到」。
                    // 但**不能静默**：这里一旦吞掉的是真实 bug，测试只会报「建出 0 个任务」，
                    // 事后完全无从排查（2026-10-23 就踩过这个坑，排查多花了一轮）。
                    // 收 Throwable 而不是 Exception：Error 同样会让这个线程悄悄消失。
                    System.out.println("【线程 " + idx + " 没抢到】" + e.getClass().getName()
                            + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).as("所有线程应在 60 秒内结束").isTrue();
        pool.shutdownNow();
        return succeeded.get();
    }

    @FunctionalInterface
    interface RunnableWithIndex {
        boolean run(int index) throws Exception;
    }

    @Test
    void 并发扣费_一百个线程抢十点积分_恰好十次成功() throws Exception {
        Long uid = newUser(10);

        int ok = runConcurrently(100, 50, i -> userRepo.deductPoints(uid, 1) == 1);
        int left = userRepo.findById(uid).orElseThrow().getPoints();

        System.out.printf("%n【并发扣费】100 个线程各扣 1 积分，余额 10%n");
        System.out.printf("  扣费成功 %d 次，最终余额 %d%n", ok, left);

        assertThat(ok).as("余额只够 10 次，成功次数必须恰好是 10").isEqualTo(10);
        assertThat(left).as("余额扣到 0 就停").isZero();
    }

    @Test
    void 并发扣费_余额永远不会变成负数() throws Exception {
        Long uid = newUser(5);

        int ok = runConcurrently(50, 25, i -> userRepo.deductPoints(uid, 1) == 1);
        int left = userRepo.findById(uid).orElseThrow().getPoints();

        System.out.printf("%n【防超扣】50 个线程各扣 1 积分，余额 5%n");
        System.out.printf("  扣费成功 %d 次，最终余额 %d%n", ok, left);

        assertThat(left).as("余额不能是负数，这是条件更新最核心的承诺").isGreaterThanOrEqualTo(0);
        assertThat(ok).isEqualTo(5);
    }

    @Test
    void 并发提交同一个来源_应该只建出一个任务() throws Exception {
        Long uid = newUser(100000);
        // 让异步处理卡住，任务停在 PROCESSING —— 这才是「去重窗口」的真实形态
        when(pythonCoreClient.convert(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(3000);
            return Map.of("note", "# n", "raw_text", "x", "duration", 0,
                    "usage", Map.of("tokens", 100));
        });

        String text = "same-source-" + System.nanoTime();
        Set<Long> taskIds = ConcurrentHashMap.newKeySet();
        List<Integer> okCount = new ArrayList<>();

        int workers = 10;
        runConcurrently(workers, workers, i -> {
            UserContext.set(uid);
            try {
                TaskRequest req = new TaskRequest();
                req.setType("document");
                req.setText(text);
                var resp = taskService.createTask(req);
                taskIds.add(resp.getTaskId());
                return true;
            } finally {
                UserContext.clear();
            }
        });

        System.out.printf("%n【幂等去重】10 个线程同时提交同一份来源%n");
        System.out.printf("  实际建出 %d 个任务，taskId = %s%n", taskIds.size(), taskIds);

        assertThat(taskIds.size())
                .as("同一用户、同一来源、同时在途 —— 应该复用同一个任务，而不是建出多个")
                .isEqualTo(1);
    }
}
