package com.limo.dto;

import com.limo.common.TaskStatus;
import com.limo.entity.Task;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 任务响应 */
@Data
@AllArgsConstructor
public class TaskResponse {
    private Long taskId;
    private String status;      // PENDING / PROCESSING / DONE / FAILED
    private String note;        // DONE 后是笔记
    private Integer tokens;     // 消耗 token
    private Integer points;     // 消耗积分
    private String failReason;  // FAILED 时说明原因：NO_POINTS / CORE_DOWN / BAD_INPUT / UNKNOWN

    /** 刚建好的任务：只有 id 和 PENDING */
    public static TaskResponse pending(Long taskId) {
        return new TaskResponse(taskId, TaskStatus.PENDING, null, 0, 0, null);
    }

    /** 从实体转过来 */
    public static TaskResponse of(Task t) {
        return new TaskResponse(t.getId(), t.getStatus(), t.getResultText(),
                t.getTokens(), t.getPoints(), t.getFailReason());
    }
}
