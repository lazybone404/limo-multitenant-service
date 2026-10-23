package com.limo;

import com.limo.client.PythonCoreClient;
import com.limo.common.TaskStatus;
import com.limo.config.UserContext;
import com.limo.dto.TaskRequest;
import com.limo.dto.TaskResponse;
import com.limo.entity.Task;
import com.limo.entity.User;
import com.limo.repository.TaskRepository;
import com.limo.repository.UserRepository;
import com.limo.service.TaskRecovery;
import com.limo.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 去重键的正确性，和「卡死任务」的回收。
 *
 * 这两个 test 对应的都是真出过问题的地方：
 *   ① 去重键一度用「来源」算，而来源是给用户看的展示字段、文本被截断到 100 字。
 *      于是两段前 100 字相同、后面完全不同的文本得到同一个键，后提交的那份被静默
 *      当成重复提交，用户拿到的是前一份的笔记 —— 不报错，只是内容错了。
 *   ② 任务认领后进程挂掉，它会永远停在 PROCESSING，并让去重键一直占着，
 *      用户从此提交不了这一份来源。
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskDedupTest {

    @Autowired UserRepository userRepo;
    @Autowired TaskRepository taskRepo;
    @Autowired TaskService taskService;
    @Autowired TaskRecovery recovery;

    @MockitoBean PythonCoreClient pythonCoreClient;

    private Long newUser() {
        return userRepo.save(User.builder()
                .username("dd_" + System.nanoTime())
                .password("x").points(100000).planCode("free").status("ACTIVE")
                .build()).getId();
    }

    private TaskResponse submit(Long uid, String text) {
        UserContext.set(uid);
        try {
            TaskRequest req = new TaskRequest();
            req.setType("document");
            req.setText(text);
            return taskService.createTask(req);
        } finally {
            UserContext.clear();
        }
    }

    /** 让后台处理卡住，任务停在 PROCESSING —— 这才是「在途」的真实形态 */
    private void blockProcessing() {
        when(pythonCoreClient.convert(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(2000);
            return Map.of("note", "# n", "raw_text", "x", "duration", 0,
                    "usage", Map.of("tokens", 100));
        });
    }

    @Test
    void 前100字相同的两段不同文本_必须建出两个任务() {
        blockProcessing();
        Long uid = newUser();

        String head = "这是一段会被截断到一百个字符的正文，用来验证去重键不能建立在截断值上面。";
        String a = head + "后半段完全不同：讲的是缓存击穿的互斥锁方案。";
        String b = head + "后半段也完全不同：讲的是消息队列的重复消费如何用幂等表兜住。";

        TaskResponse ra = submit(uid, a);
        TaskResponse rb = submit(uid, b);

        System.out.printf("%n【去重键】两段前 100 字相同的文本%n");
        System.out.printf("  任务 A = %s，任务 B = %s%n", ra.getTaskId(), rb.getTaskId());

        assertThat(rb.getTaskId())
                .as("两段文本只有后半段不同，但那是它们各自的内容 —— 不能被判成重复提交")
                .isNotEqualTo(ra.getTaskId());
    }

    @Test
    void 同一段文本重复提交_应该复用同一个任务() {
        blockProcessing();
        Long uid = newUser();
        String text = "完全相同的正文 " + System.nanoTime();

        TaskResponse r1 = submit(uid, text);
        TaskResponse r2 = submit(uid, text);

        System.out.printf("%n【去重键】同一段文本提交两次%n");
        System.out.printf("  第一次 = %s，第二次 = %s%n", r1.getTaskId(), r2.getTaskId());

        assertThat(r2.getTaskId()).isEqualTo(r1.getTaskId());
    }

    @Test
    void 卡死在PROCESSING的任务会被回收_并释放去重键() {
        Long uid = newUser();
        String text = "卡死的来源 " + System.nanoTime();

        // 直接造一条「认领后就没再回来」的任务：状态 PROCESSING、开始时刻是 30 分钟前
        Task dead = taskRepo.save(Task.builder()
                .userId(uid).type("document").source(text).status(TaskStatus.PROCESSING)
                .processingStartedAt(LocalDateTime.now().minusMinutes(30))
                .dedupKey("dead-key-" + System.nanoTime())
                .build());

        int n = recovery.recoverStale(Duration.ofMinutes(10));
        Task after = taskRepo.findById(dead.getId()).orElseThrow();

        System.out.printf("%n【超时回收】回收了 %d 个任务，任务 %s -> %s / %s%n",
                n, dead.getId(), after.getStatus(), after.getFailReason());

        assertThat(n).isGreaterThanOrEqualTo(1);
        assertThat(after.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(after.getFailReason())
                .as("要告诉用户这是超时，不是「核心不可用」——前者能重试，后者要等")
                .isEqualTo(TaskStatus.TIMEOUT);
        assertThat(after.getDedupKey())
                .as("去重键必须释放，否则这一份来源从此再也提交不了")
                .isNull();
    }

    @Test
    void 正常处理中的任务_不该被回收() {
        Long uid = newUser();
        Task alive = taskRepo.save(Task.builder()
                .userId(uid).type("document").source("还在处理 " + System.nanoTime())
                .status(TaskStatus.PROCESSING)
                .processingStartedAt(LocalDateTime.now().minusMinutes(1))   // 刚认领一分钟
                .dedupKey("alive-key-" + System.nanoTime())
                .build());

        recovery.recoverStale(Duration.ofMinutes(10));

        Task after = taskRepo.findById(alive.getId()).orElseThrow();
        assertThat(after.getStatus())
                .as("刚开始处理一分钟的任务不能被当成卡死杀掉")
                .isEqualTo(TaskStatus.PROCESSING);
        assertThat(after.getDedupKey()).isNotNull();
    }
}
