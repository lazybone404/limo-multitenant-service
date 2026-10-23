package com.limo.controller;

import com.limo.common.ApiResponse;
import com.limo.dto.NoteResponse;
import com.limo.dto.UpdateNoteRequest;
import com.limo.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 笔记接口（需登录）：/api/notes */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /** 我的笔记列表（可带 page / size，不传时 page=0、size=200） */
    @GetMapping
    public ApiResponse<List<NoteResponse>> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "200") int size) {
        return ApiResponse.ok(noteService.myNotes(page, size));
    }

    /** 笔记详情 */
    @GetMapping("/{id}")
    public ApiResponse<NoteResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(noteService.get(id));
    }

    /** 编辑笔记（保存 markdown 原文，覆盖当前版本） */
    @PutMapping("/{id}")
    public ApiResponse<NoteResponse> update(@PathVariable Long id, @RequestBody UpdateNoteRequest req) {
        return ApiResponse.ok(noteService.update(id, req.getTitle(), req.getContent()));
    }

    /** 删除笔记（释放配额） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return ApiResponse.ok(null);
    }
}
