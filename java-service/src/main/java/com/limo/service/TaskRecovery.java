package com.limo.service;

import com.limo.common.TaskStatus;
import com.limo.entity.Task;
import com.limo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收「认领了但再也没回来」的任务。
 *
 * 要解决的问题：任务被线程认领、状态变成 PROCESSING，然后进程在处理的途中挂了（重启、部署、崩溃）。
 * 这个任务就永远停在 PROCESSING，而且因为去重键还挂在它身上，
 * 用户之后再提交同一份来源，会被去重逻辑拦下来、把这条死任务还给他 —— 从此这个来源再也提交不了。
 *
 * 判断依据是 processingStartedAt，不是 createdAt：
 * 任务是先排队再处理的，高峰期排了十分钟队才开始，用 createdAt 判断会把正常任务误杀。
 *
 * 为什么用定时扫描而不是队列自带的可见性超时：异步用的是进程内线程池，没有队列中间件
 * （见 README「已知缺口」）。换成 MQ 之后这一段可以删掉，改由队列的 ack 超时机制接管。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecovery {

    /** 超过这个时长还在 PROCESSING，就认为认领它的线程已经不在了 */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final TaskRepository taskRepo;

    @Value("${limo.recovery.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${limo.recovery.interval-ms:60000}",
               initialDelayString = "${limo.recovery.initial-delay-ms:60000}")
    public void scheduledRecover() {
        if (!enabled) return;
        try {
            int n = recoverStale(STALE_AFTER);
            if (n > 0) log.warn("回收了 {} 个卡在 PROCESSING 的任务", n);
        } catch (Exception e) {
            // 回收本身挂了不该把调度器带停，也不该影响正常提交
            log.error("超时任务回收失败", e);
        }
    }

    /**
     * 扫一遍并回收，返回回收条数。
     * 拆成独立方法是为了让测试能直接调，不必等一分钟的调度周期。
     */
    public int recoverStale(Duration staleAfter) {
        LocalDateTime before = LocalDateTime.now().minus(staleAfter);
        List<Task> stale = taskRepo.findByStatusAndProcessingStartedAtBefore(
                TaskStatus.PROCESSING, before);
        if (stale.isEmpty()) return 0;

        int n = 0;
        for (Task t : stale) {
            // 逐条独立提交：一条失败不该连累其余，
            // 而且这里不需要整体事务 —— 每条任务只改它自己那几列。
            try {
                t.setStatus(TaskStatus.FAILED);
                t.setFailReason(TaskStatus.TIMEOUT);
                t.setFinishedAt(LocalDateTime.now());
                // 关键：把去重键让开，否则用户永远提交不了这一份来源
                t.setDedupKey(null);
                taskRepo.save(t);
                log.warn("任务 {} 从 {} 起停在 PROCESSING 已超过 {}，标记为 FAILED(TIMEOUT) 并释放去重键",
                        t.getId(), t.getProcessingStartedAt(), staleAfter);
                n++;
            } catch (Exception e) {
                log.error("回收任务 {} 失败", t.getId(), e);
            }
        }
        return n;
    }
}
