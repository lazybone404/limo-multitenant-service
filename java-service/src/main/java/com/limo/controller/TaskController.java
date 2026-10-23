package com.limo.controller;

import com.limo.common.ApiResponse;
import com.limo.dto.TaskRequest;
import com.limo.dto.TaskResponse;
import com.limo.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 转换任务接口：提交 / 查询（需登录，JwtInterceptor 鉴权）。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 提交转笔记任务：POST /api/tasks  body {type, url?, text?} */
    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskRequest req) {
        return ApiResponse.ok(taskService.createTask(req));
    }

    /** 上传文件转笔记：POST /api/tasks/upload  multipart(file + type) */
    @PostMapping("/upload")
    public ApiResponse<TaskResponse> upload(@RequestParam("file") MultipartFile file, @RequestParam("type") String type) {
        return ApiResponse.ok(taskService.createTaskFromFile(file, type));
    }

    /** 查任务状态/结果：GET /api/tasks/{id} */
    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(taskService.getTask(id));
    }
}
