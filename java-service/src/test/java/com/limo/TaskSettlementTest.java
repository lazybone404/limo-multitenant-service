package com.limo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limo.client.PythonCoreClient;
import com.limo.common.TaskStatus;
import com.limo.entity.User;
import com.limo.repository.NoteRepository;
import com.limo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 结算的事务性测试 —— 也就是「失败不扣」这四个字到底成不成立。
 *
 * 原先的顺序是「先扣积分，再落笔记」。落笔记那一步如果失败，异常被 catch 掉、
 * 只把任务标成 FAILED，**积分已经扣掉了不会退**。所以「失败不扣」在那种写法下是空话。
 *
 * 现在扣积分 + 落笔记 + 改状态在同一个事务里，任何一步抛异常整体回滚。
 * 这里把 NoteRepository 换成 mock、故意让 save 抛异常，就是为了看积分有没有被退回去。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskSettlementTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepo;

    @MockitoBean PythonCoreClient pythonCoreClient;
    @MockitoBean NoteRepository noteRepo;   // 让它 save 抛异常，模拟「落库失败」

    private String register(String u) throws Exception {
        String r = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"p1\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(r).path("data").path("token").asText();
    }

    /** 提交一个任务，轮询到终态，返回 [status, failReason] */
    private String[] runToTerminal(String token, String text) throws Exception {
        when(pythonCoreClient.convert(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("note", "# 笔记", "raw_text", "x", "duration", 0,
                        "usage", Map.of("tokens", 3000)));

        String resp = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"document\",\"text\":\"" + text + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long taskId = om.readTree(resp).path("data").path("taskId").asLong();

        String status = "";
        String reason = "";
        for (int i = 0; i < 60; i++) {
            String r = mockMvc.perform(get("/api/tasks/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            status = om.readTree(r).path("data").path("status").asText();
            reason = om.readTree(r).path("data").path("failReason").asText();
            if (TaskStatus.DONE.equals(status) || TaskStatus.FAILED.equals(status)) break;
            Thread.sleep(200);
        }
        return new String[]{status, reason};
    }

    @Test
    void 落库失败时_已经扣掉的积分要回滚() throws Exception {
        when(noteRepo.countByUserId(any())).thenReturn(0L);
        when(noteRepo.maxVersion(any(), any())).thenReturn(0);
        when(noteRepo.save(any())).thenThrow(new RuntimeException("模拟落库失败"));

        String token = register("settle_rollback");
        int before = points("settle_rollback");

        String[] r = runToTerminal(token, "rollback case " + System.nanoTime());

        assertThat(r[0]).isEqualTo(TaskStatus.FAILED);
        assertThat(points("settle_rollback"))
                .as("落库失败，扣掉的积分必须原样退回 —— 这就是「失败不扣」")
                .isEqualTo(before);
    }

    @Test
    void 积分不够时_任务失败且原因是无需重试的NO_POINTS() throws Exception {
        when(noteRepo.countByUserId(any())).thenReturn(0L);

        String token = register("settle_no_points");
        // 让核心自报一个远超余额的消耗：1000000 积分
        when(pythonCoreClient.convert(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("note", "# 笔记", "raw_text", "x", "duration", 0,
                        "usage", Map.of("tokens", 999_999_999)));

        String resp = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"document\",\"text\":\"expensive " + System.nanoTime() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long taskId = om.readTree(resp).path("data").path("taskId").asLong();

        String status = "";
        String reason = "";
        for (int i = 0; i < 60; i++) {
            String r = mockMvc.perform(get("/api/tasks/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            status = om.readTree(r).path("data").path("status").asText();
            reason = om.readTree(r).path("data").path("failReason").asText();
            if (TaskStatus.DONE.equals(status) || TaskStatus.FAILED.equals(status)) break;
            Thread.sleep(200);
        }

        assertThat(status).isEqualTo(TaskStatus.FAILED);
        assertThat(reason)
                .as("积分不够，用户该去充值；不能报成「核心不可用，请重试」")
                .isEqualTo(TaskStatus.NO_POINTS);

        // mock 成 0 也不该扣成负数
        assertThat(points("settle_no_points")).isGreaterThanOrEqualTo(0);
    }

    private int points(String username) {
        return userRepo.findByUsername(username).map(User::getPoints).orElse(0);
    }
}
