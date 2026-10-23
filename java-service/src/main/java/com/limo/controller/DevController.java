package com.limo.controller;

import com.limo.common.ApiResponse;
import com.limo.config.UserContext;
import com.limo.entity.Note;
import com.limo.entity.Source;
import com.limo.repository.NoteRepository;
import com.limo.repository.SourceRepository;
import com.limo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【仅开发环境】测试/调试端点，@Profile("dev") —— prod 不加载。
 * 用于快速生成演示笔记、调积分、塞配额，方便前端开发测试（不依赖 python 核心）。
 * 启动 dev：java -jar xxx.jar --spring.profiles.active=dev
 */
@Slf4j
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final UserRepository userRepo;
    private final SourceRepository sourceRepo;
    private final NoteRepository noteRepo;

    /** mock 生成一篇演示笔记（不调 python，直接落库 + 模拟扣 1 积分） */
    @PostMapping("/demo-note")
    public ApiResponse<Note> demoNote() {
        Long uid = UserContext.get();
        int cost = 1;
        userRepo.deductPoints(uid, cost);

        Source src = sourceRepo.save(Source.builder()
                .userId(uid).sourceType("document").origin("dev-demo").title("演示来源").build());

        String md = "# 演示笔记\n\n## 核心要点\n- 这是一篇开发环境生成的演示笔记\n- 用于快速预览前端笔记渲染效果\n- 来源：dev-demo（未调用 python 核心）\n\n## 详情\n- 可在此基础上继续编辑、查看 TOC、切换版本";
        int version = noteRepo.findByUserIdAndSourceIdOrderByVersionDesc(uid, src.getId()).isEmpty()
                ? 1 : noteRepo.findByUserIdAndSourceIdOrderByVersionDesc(uid, src.getId()).get(0).getVersion() + 1;

        Note n = noteRepo.save(Note.builder()
                .userId(uid).sourceId(src.getId()).version(version)
                .content(md).title("演示笔记").preview("这是一篇开发环境生成的演示笔记…")
                .type("document").tokens(50).points(cost).durationSec(0).build());
        return ApiResponse.ok("已生成演示笔记", n);
    }

    /** 给当前用户加积分（测扣费/订阅） */
    @PostMapping("/add-points")
    public ApiResponse<Map<String, Object>> addPoints(@RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.get();
        int pts = body != null && body.get("points") instanceof Number n ? n.intValue() : 100;
        userRepo.addPoints(uid, pts);
        return ApiResponse.ok("已加 " + pts + " 积分", userInfo(uid));
    }

    /** 给当前用户塞 N 篇笔记（测配额拦截） */
    @PostMapping("/fill-notes")
    public ApiResponse<Map<String, Object>> fillNotes(@RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.get();
        int count = body != null && body.get("count") instanceof Number n ? n.intValue() : 5;
        Source src = sourceRepo.save(Source.builder()
                .userId(uid).sourceType("document").origin("dev-fill").title("填充来源").build());
        for (int i = 0; i < count; i++) {
            noteRepo.save(Note.builder().userId(uid).sourceId(src.getId()).version(i + 1)
                    .content("# 填充笔记 " + (i + 1)).title("填充 " + (i + 1)).preview("配额测试")
                    .type("document").tokens(10).points(0).durationSec(0).build());
        }
        return ApiResponse.ok("已填充 " + count + " 篇", noteCount(uid));
    }

    private Map<String, Object> userInfo(Long uid) {
        Map<String, Object> m = new LinkedHashMap<>();
        userRepo.findById(uid).ifPresent(u -> {
            m.put("points", u.getPoints());
            m.put("planCode", u.getPlanCode());
        });
        m.put("noteCount", noteRepo.countByUserId(uid));
        return m;
    }

    private Map<String, Object> noteCount(Long uid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("noteCount", noteRepo.countByUserId(uid));
        return m;
    }
}
