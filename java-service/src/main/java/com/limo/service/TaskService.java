package com.limo.service;

import com.limo.common.BizException;
import com.limo.common.TaskStatus;
import com.limo.config.UserContext;
import com.limo.dto.TaskRequest;
import com.limo.dto.TaskResponse;
import com.limo.entity.Note;
import com.limo.entity.Plan;
import com.limo.entity.Source;
import com.limo.entity.Task;
import com.limo.entity.User;
import com.limo.repository.NoteRepository;
import com.limo.repository.PlanRepository;
import com.limo.repository.SourceRepository;
import com.limo.repository.TaskRepository;
import com.limo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 转换任务业务（核心）：
 * 提交 -> 配额校验 -> 建/复用 source -> 异步调核心 -> 按核心 usage 扣积分 -> 落 note(带版本) -> DONE。
 *
 * 设计点：幂等去重、慢任务异步(留 MQ 口)、条件扣积分(留分布式锁口)、数据隔离、note 配额(免费/付费不同)。
 *
 * 2026-10-13 修的四件事（每条的理由都写在对应位置的注释里）：
 *   ① 扣费与落库不再分家 —— 用 TransactionTemplate 绑成一个事务，「失败不扣」才真成立
 *   ② 任务状态改成条件更新认领 —— 不再先读出来、改字段、再 save
 *   ③ 失败不再只落一个 FAILED —— 按「用户能采取的行动」分成四种原因
 *   ④ 两个几乎一样的处理方法合并成一条路径
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;
    private final NoteRepository noteRepo;
    private final SourceRepository sourceRepo;
    private final PlanRepository planRepo;
    private final TaskProcessor taskProcessor;   // 接口，当前=调 python；进阶可换 MQ
    private final Executor taskExecutor;          // 线程池
    private final TransactionTemplate tx;         // 扣积分 + 落 note + 改状态绑成一个事务

    // ==================== 提交 ====================

    /** 提交任务（文本 / URL）：立即返回 taskId（PENDING），后台异步处理 */
    public TaskResponse createTask(TaskRequest req) {
        Long uid = UserContext.get();
        Task task = submit(uid, req.getType(), sourceOf(req), identityOf(req));

        // 命中幂等去重：把已有任务原样还回去，不再建、不再扣
        if (!TaskStatus.PENDING.equals(task.getStatus())) return TaskResponse.of(task);

        final Long id = task.getId();
        final Long sourceId = task.getSourceId();
        taskExecutor.execute(() -> run(id, uid, sourceId, req.getType(),
                () -> taskProcessor.process(uid, id, req.getType(), req.getUrl(),
                        req.getText(), req.getStyle(), req.getOptions())));
        return TaskResponse.pending(id);
    }

    /** 提交任务（上传文件）：存临时文件 -> 建任务 -> 异步处理 */
    public TaskResponse createTaskFromFile(MultipartFile file, String type) {
        Long uid = UserContext.get();
        String origin = "file:" + (file == null ? "doc" : file.getOriginalFilename());

        // 临时文件名用 UUID，不用毫秒时间戳：同一毫秒里两个上传会拿到同一个名字，
        // 后落盘的那个把前一个覆盖掉，用户拿到的是别人文件的处理结果。
        // 扩展名保留，processFile 靠它判断类型。
        File tmp = new File(System.getProperty("java.io.tmpdir"), "limo_" + newTmpName(file));
        try {
            file.transferTo(tmp);
        } catch (Exception e) {
            log.warn("用户 {} 上传文件落盘失败：{}", uid, e.getMessage(), e);
            throw new BizException(400, "文件保存失败");
        }

        Task task;
        try {
            // 去重键用**文件内容的 SHA-256**，不是文件名 + 大小。
            // 只看名字会把「同名但内容不同」的两份误判成同一份（用户拿不到自己那份的结果），
            // 只看名字 + 大小还会漏掉「改了两个字但长度没变」的情况。
            task = submit(uid, type, origin, "file|" + sha256Of(tmp));
        } catch (RuntimeException e) {
            tmp.delete();
            throw e;
        }
        if (!TaskStatus.PENDING.equals(task.getStatus())) {
            tmp.delete();
            return TaskResponse.of(task);
        }

        final Long id = task.getId();
        final Long sourceId = task.getSourceId();
        taskExecutor.execute(() -> {
            try {
                run(id, uid, sourceId, type, () -> taskProcessor.processFile(uid, id, type, tmp.getAbsolutePath()));
            } finally {
                tmp.delete();   // 成功或失败都清掉临时文件
            }
        });
        return TaskResponse.pending(id);
    }

    /**
     * 提交路径的公共部分：配额校验 -> 幂等去重 -> 建/复用 source -> 建任务。
     *
     * 文本版和文件版原先各写了一遍这三行配额校验，两处都会漏改。
     * 现在只有这一个入口。返回的既可能是新建的 PENDING 任务，也可能是在途的既有任务。
     *
     * 2026-10-23 改：去重从「查了再建」换成「唯一索引兜底」。
     * 原来那段先查在途任务、没有就建，看起来对，但它只在单线程下对——
     * 实测让 10 个线程同时提交同一份来源，建出了 10 个任务（`ConcurrencyEvidenceTest`）。
     * 现在先算一个 dedupKey 写进唯一索引列：并发的两个请求，一个能插进去，
     * 另一个撞索引，然后回来查那条已经存在的数据，这样「同一来源同时只处理一份」才真成立。
     *
     * @param identity 去重键的原料，必须能唯一区分一份内容：URL 原文、文本全文、文件内容哈希。
     *                 传 null 表示这份输入没有可比对的同一性（比如空输入的兜底），不参与去重。
     */
    private Task submit(Long uid, String type, String origin, String identity) {
        checkNoteQuota(uid);

        final String key = (identity == null) ? null : dedupKey(uid, type, identity);

        // 先查一次：绝大多数提交（没有并发）在这一步就命中，不必靠异常做控制流
        Optional<Task> dup = key == null ? Optional.empty() : taskRepo.findFirstByDedupKey(key);
        if (dup.isPresent()) {
            log.info("用户 {} 的来源已有在途任务 {}，直接复用", uid, dup.get().getId());
            return dup.get();
        }

        Source src = sourceRepo.findFirstByUserIdAndOriginAndSourceType(uid, origin, type)
                .orElseGet(() -> sourceRepo.save(Source.builder()
                        .userId(uid).sourceType(type).origin(origin).title(guessTitle(origin)).build()));

        Task task = Task.builder().userId(uid).type(type).source(origin)
                .sourceId(src.getId()).status(TaskStatus.PENDING).dedupKey(key).build();
        try {
            task = taskRepo.saveAndFlush(task);
        } catch (DataIntegrityViolationException e) {
            // 上面那次查和这次插入之间，别的线程已经把同一个 key 插进去了。
            // 唯一索引是最后一道闸：这里撞到，说明任务已经存在，把它返回就行。
            //
            // 但撞的那一刻，赢的那一方**可能还没提交** —— READ COMMITTED 下看不到未提交的行，
            // 直接查会查不到，于是这个请求白白失败（实测 10 个线程里 9 个都这样挂掉）。
            // 所以这里等一小会儿再查：对方走的是同一条代码路径，提交在毫秒级。
            for (int i = 0; i < 20; i++) {
                Optional<Task> found = taskRepo.findFirstByDedupKey(key);
                if (found.isPresent()) {
                    log.info("用户 {} 的来源并发提交，唯一索引拦住重复插入，复用任务 {}",
                            uid, found.get().getId());
                    return found.get();
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // 二十次（约半秒）都读不到，就不是「并发重复提交」了，是别的问题。
            // 吞掉它会让排查变得无迹可寻，所以原样往上抛。
            log.error("用户 {} 提交时撞了唯一索引，但等了 500ms 仍查不到既有任务", uid, e);
            throw new BizException(500, "任务创建失败，请重试");
        }
        log.info("用户 {} 提交任务 {}（type={}, source={}）", uid, task.getId(), type, origin);
        return task;
    }

    /**
     * 去重键：同一用户 + 同一类型 + 同一份内容 的 sha256。
     *
     * ⚠️ 原料必须是 **identity**（完整输入），不能是 origin。
     * origin 是给用户看的展示字段，文本会被截断到 100 字（见 sourceOf）。用截断值算键，
     * 两段前 100 字相同、后面完全不同的文本就会得到同一个键 —— 后提交的那份被静默当成
     * 重复提交，用户拿到的是前一份的笔记，而且不会报错。这一条踩过。
     */
    private String dedupKey(Long uid, String type, String identity) {
        return sha256Hex(uid + "|" + type + "|" + identity);
    }

    /** 未截断的输入本身，作为去重键的原料。空输入返回 null（不参与去重）。 */
    private String identityOf(TaskRequest req) {
        if (req.getUrl() != null && !req.getUrl().isBlank()) return "url|" + req.getUrl().trim();
        if (req.getText() != null && !req.getText().isBlank()) return "text|" + req.getText();
        return null;
    }

    private String sha256Hex(String raw) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return toHex(h);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 规范要求必须支持的算法，取不到说明运行环境坏了。
            // 这不是业务失败，别包成 BizException 让用户看到一句「请重试」。
            throw new IllegalStateException("JVM 不支持 SHA-256，无法计算去重键", e);
        }
    }

    /** 文件内容的 sha256，流式读，不把整份文件读进内存。 */
    private String sha256Of(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        } catch (IOException e) {
            throw new BizException(400, "文件读取失败");
        }
    }

    private static String toHex(byte[] h) {
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 临时文件名：UUID + 原扩展名。 */
    private static String newTmpName(MultipartFile file) {
        String name = (file == null || file.getOriginalFilename() == null)
                ? "doc" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = (dot > 0 && name.length() - dot <= 10) ? name.substring(dot) : "";
        return UUID.randomUUID() + ext;
    }

    // ==================== 执行（两种提交共用的唯一路径）====================

    /** 后台执行：认领 -> 调核心 -> 结算。文本版和文件版都走这里，差别只在传进来的 supplier。 */
    private void run(Long taskId, Long uid, Long sourceId, String type, Supplier<TaskProcessResult> call) {
        // 认领：只有还是 PENDING 才能进 PROCESSING，返回 0 说明已经被别人处理过。
        // 原先的写法是先查出来、setStatus、再 save —— 两个线程能同时认领同一个任务，
        // 结果是同一个来源被处理两遍、扣两次积分。
        if (taskRepo.compareAndSetStatus(taskId, TaskStatus.PENDING, TaskStatus.PROCESSING,
                LocalDateTime.now()) == 0) {
            log.warn("任务 {} 不在 PENDING（已被处理或不存在），本次跳过", taskId);
            return;
        }
        try {
            settle(taskId, uid, sourceId, type, call.get());
        } catch (Exception e) {
            markFailed(taskId, e);
        }
    }

    /**
     * 结算：扣积分 + 落笔记 + 任务置 DONE，三件事在同一个事务里。
     *
     * 为什么必须绑成一个事务：原先的顺序是「先扣积分，再落笔记」。落笔记那一步如果失败，
     * 异常被 catch 掉、只把任务标成 FAILED —— **积分已经扣掉了，不会退**。
     * 对外承诺的「失败不扣」在这种写法下是不成立的。现在任何一步抛异常，整个事务回滚。
     */
    private void settle(Long taskId, Long uid, Long sourceId, String type, TaskProcessResult r) {
        final int tokens = r.tokens();
        final int cost = TokenCalculator.costPoints(tokens);

        tx.executeWithoutResult(st -> {
            // 条件更新扣积分：余额不够时返回 0，不会扣成负数
            if (userRepo.deductPoints(uid, cost) == 0) {
                throw new BizException(402, "积分不足，本次消耗 " + cost + " 积分");
            }

            int version = noteRepo.maxVersion(uid, sourceId) + 1;
            noteRepo.save(Note.builder()
                    .userId(uid).sourceId(sourceId).version(version)
                    .content(r.note()).title(guessTitle(type)).preview(preview(r.note()))
                    .type(type).tokens(tokens).points(cost).durationSec(r.durationSec())
                    .build());

            Task t = taskRepo.findById(taskId).orElseThrow(() -> new BizException(500, "任务不见了：" + taskId));
            t.setStatus(TaskStatus.DONE);
            t.setResultText(r.note());
            t.setTokens(tokens);
            t.setPoints(cost);
            t.setDurationSec(r.durationSec());
            t.setFinishedAt(LocalDateTime.now());
            t.setDedupKey(null);   // 处理完了，把去重窗口让开：同一来源可以再生成一次
            taskRepo.save(t);
        });

        log.info("任务 {} 完成：{} token / {} 积分", taskId, tokens, cost);
    }

    /** 失败落定：状态 + 原因。原因按「用户能采取的行动」分，不按异常类型分。 */
    private void markFailed(Long taskId, Exception e) {
        String reason = TaskStatus.reasonOf(e);
        if (TaskStatus.UNKNOWN.equals(reason)) {
            // 没归类的失败必须留堆栈，否则线上只能看到一句 FAILED，什么也查不到
            log.error("任务 {} 失败（未分类）：{}", taskId, e.getMessage(), e);
        } else {
            log.warn("任务 {} 失败（{}）：{}", taskId, reason, e.getMessage());
        }
        taskRepo.findById(taskId).ifPresent(t -> {
            t.setStatus(TaskStatus.FAILED);
            t.setFailReason(reason);
            t.setFinishedAt(LocalDateTime.now());
            t.setDedupKey(null);   // 失败了更得让开：用户改完就该能重试，不能被自己的上次失败挡住
            taskRepo.save(t);
        });
    }

    // ==================== 查询 ====================

    /** 查任务（校验归属：数据隔离） */
    public TaskResponse getTask(Long taskId) {
        Long uid = UserContext.get();
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new BizException(404, "任务不存在"));
        // 不属于自己时也报 404：不告诉调用方「这个 id 存在，只是不是你的」
        if (!t.getUserId().equals(uid)) throw new BizException(404, "任务不存在");
        return TaskResponse.of(t);
    }

    // ==================== 工具 ====================

    /** 笔记配额：已有笔记数 >= 套餐上限就拒（在扣积分之前拦） */
    private void checkNoteQuota(Long uid) {
        Plan plan = currentPlan(uid);
        if (noteRepo.countByUserId(uid) >= plan.getNoteLimit()) {
            throw new BizException(409, "已达笔记上限 " + plan.getNoteLimit() + " 篇，请升级套餐或删除旧笔记");
        }
    }

    /**
     * 取用户当前套餐。找不到就回落 free；free 也没有说明数据坏了 —— 显式报错。
     * 原先写的是 findByCode(用户套餐).orElseGet(() -> findByCode("free").orElse(null))，
     * 拿到 null 之后调用方直接 .getNoteLimit() → NPE，而且 NPE 的堆栈指不到真正的原因。
     */
    private Plan currentPlan(Long uid) {
        User u = userRepo.findById(uid).orElseThrow(() -> new BizException(404, "用户不存在"));
        return planRepo.findByCode(u.getPlanCode())
                .or(() -> planRepo.findByCode("free"))
                .orElseThrow(() -> new BizException(500,
                        "套餐配置缺失：既没有 " + u.getPlanCode() + "，也没有 free"));
    }

    private String sourceOf(TaskRequest req) {
        if (req.getUrl() != null && !req.getUrl().isBlank()) return req.getUrl();
        if (req.getText() != null && !req.getText().isBlank())
            return req.getText().substring(0, Math.min(100, req.getText().length()));
        return "t" + System.currentTimeMillis();
    }

    private String guessTitle(String s) {
        if (s == null) return "笔记";
        String t = s.trim();
        return t.length() > 50 ? t.substring(0, 50) : t;
    }

    private String preview(String note) {
        if (note == null) return "";
        String n = note.replace("\n", " ").trim();
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
