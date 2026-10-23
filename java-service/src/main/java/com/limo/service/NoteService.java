package com.limo.service;

import com.limo.common.BizException;
import com.limo.config.UserContext;
import com.limo.dto.NoteResponse;
import com.limo.entity.Note;
import com.limo.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 笔记资产业务：列表/详情/删除（数据隔离——只能操作自己的笔记）。
 * 删除会释放配额（笔记上限按 count 计算，删掉就回到限制内）。
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    /** 列表默认返回的条数上限。免费套餐最多 30 篇，200 对当前配额绰绰有余。 */
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 500;

    private final NoteRepository noteRepo;

    public List<NoteResponse> myNotes() {
        return myNotes(0, DEFAULT_PAGE_SIZE);
    }

    /**
     * 我的笔记列表（分页）。
     * 原先是一次性把该用户所有笔记查出来返回 —— 笔记越多这个接口越慢，
     * 而且返回体没有任何上限。现在由数据库分页，size 也夹在 1..500 之间。
     */
    public List<NoteResponse> myNotes(int page, int size) {
        Long uid = UserContext.get();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return noteRepo.findByUserId(uid,
                        org.springframework.data.domain.PageRequest.of(safePage, safeSize,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .stream().map(NoteService::toResp).toList();
    }

    public NoteResponse get(Long id) {
        Long uid = UserContext.get();
        Note n = noteRepo.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new BizException(404, "笔记不存在"));
        return toResp(n);
    }

    public void delete(Long id) {
        Long uid = UserContext.get();
        Note n = noteRepo.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new BizException(404, "笔记不存在"));
        noteRepo.delete(n);
    }

    /** 编辑保存（覆盖当前版本；数据隔离） */
    public NoteResponse update(Long id, String title, String content) {
        Long uid = UserContext.get();
        Note n = noteRepo.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new BizException(404, "笔记不存在"));
        if (title != null && !title.isBlank()) n.setTitle(title);
        if (content != null) n.setContent(content);
        noteRepo.save(n);
        return toResp(n);
    }

    private static NoteResponse toResp(Note n) {
        return new NoteResponse(n.getId(), n.getSourceId(), n.getVersion(), n.getTitle(),
                n.getPreview(), n.getContent(), n.getType(), n.getTokens(), n.getPoints(), n.getCreatedAt());
    }
}
