package com.limo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limo.client.PythonCoreClient;
import com.limo.entity.Note;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试：锁住 limo 业务端核心链路（不依赖真实 python-core，用 Mockito mock 掉）。
 * 全链路：注册 → 订阅 → 提任务 → 异步调核心(mock) → 扣积分 → 落 note → DONE。
 * 覆盖：订阅送积分+笔记上限、任务成功生成笔记、配额拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LimoFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired NoteRepository noteRepo;
    @Autowired UserRepository userRepo;

    @MockitoBean
    PythonCoreClient pythonCoreClient;

    private String register(String u, String p) throws Exception {
        String r = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(r).path("data").path("token").asText();
    }

    @Test
    void 全链路_注册订阅提任务_成功生成笔记并扣积分() throws Exception {
        // 核心黑盒自报 usage（mock）
        // 签名必须和 PythonCoreClient.convert 一致：多了 style 和 options 两个参数。
        // 这里原先只写了 3 个 any()，编译期看不出来（Mockito 是运行期匹配），
        // 跑测试才报 NoSuchMethod —— 说明这 3 个测试在改用 5 参签名之后就没再跑过。
        when(pythonCoreClient.convert(any(), any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "note", "# 笔记\n\n## 要点\n- java concurrency hashmap",
                        "raw_text", "java concurrency hashmap",
                        "duration", 0,
                        "usage", Map.of("tokens", 3000, "cost", 3000)));

        String token = register("flow1", "p1");

        // 订阅 monthly → 积分+500、笔记上限 500
        mockMvc.perform(post("/api/subscribe")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"monthly\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteLimit").value(500));

        // 提交任务
        String tResp = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"document\",\"text\":\"java concurrency hashmap concurrent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long taskId = om.readTree(tResp).path("data").path("taskId").asLong();

        // 轮询任务到终态
        String status = "";
        for (int i = 0; i < 40; i++) {
            String r = mockMvc.perform(get("/api/tasks/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            status = om.readTree(r).path("data").path("status").asText();
            if ("DONE".equals(status) || "FAILED".equals(status)) break;
            Thread.sleep(300);
        }
        assertThat(status).isEqualTo("DONE");

        // 笔记已生成
        String notes = mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(notes).path("data").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 配额_免费用户塞满30篇后拒新任务() throws Exception {
        String token = register("q1", "p1");
        User u = userRepo.findByUsername("q1").orElseThrow();
        for (int i = 0; i < 30; i++) {
            noteRepo.save(Note.builder().userId(u.getId()).sourceId(1L).version(i + 1).content("x").build());
        }
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"document\",\"text\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void 编辑_保存markdown覆盖当前版本() throws Exception {
        String token = register("e1", "p1");
        User u = userRepo.findByUsername("e1").orElseThrow();
        Note saved = noteRepo.save(Note.builder().userId(u.getId()).sourceId(1L).version(1).content("# 旧内容").build());

        // 编辑：改 content
        mockMvc.perform(put("/api/notes/" + saved.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"# 新内容\\n- edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# 新内容\n- edited"));
    }
}
