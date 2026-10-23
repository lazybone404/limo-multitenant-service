package com.limo;

import com.limo.common.BizException;
import com.limo.common.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 失败原因分类的单元测试。
 *
 * 为什么值得单独测：这一层是「用户看到 FAILED 之后该做什么」的唯一依据。
 * 归错类比不归类更糟 —— 把"积分不足"说成"核心不可用，请重试"，用户重试一百次也不会好。
 */
class TaskStatusTest {

    @Test
    void 积分不足与配额满_都归到需要用户去处理的NO_POINTS() {
        assertThat(TaskStatus.reasonOf(new BizException(402, "积分不足"))).isEqualTo(TaskStatus.NO_POINTS);
        assertThat(TaskStatus.reasonOf(new BizException(409, "已达笔记上限"))).isEqualTo(TaskStatus.NO_POINTS);
    }

    @Test
    void 核心返回的502_归CORE_DOWN表示可以稍后重试() {
        assertThat(TaskStatus.reasonOf(new BizException(502, "核心处理服务暂不可用"))).isEqualTo(TaskStatus.CORE_DOWN);
    }

    @Test
    void 输入有问题_归BAD_INPUT() {
        assertThat(TaskStatus.reasonOf(new BizException(400, "文件保存失败"))).isEqualTo(TaskStatus.BAD_INPUT);
        assertThat(TaskStatus.reasonOf(new BizException(404, "任务不存在"))).isEqualTo(TaskStatus.BAD_INPUT);
    }

    @Test
    void 其余一切_包括500_都归UNKNOWN() {
        // 500 是"我们自己的 bug"，不是用户能解决的，所以不能归成"请稍后重试"
        assertThat(TaskStatus.reasonOf(new BizException(500, "套餐配置缺失"))).isEqualTo(TaskStatus.UNKNOWN);
        assertThat(TaskStatus.reasonOf(new RuntimeException("谁知道"))).isEqualTo(TaskStatus.UNKNOWN);
        assertThat(TaskStatus.reasonOf(new NullPointerException())).isEqualTo(TaskStatus.UNKNOWN);
    }

    @Test
    void 四种原因互不相同_前端能据此给不同的下一步() {
        assertThat(java.util.Set.of(TaskStatus.NO_POINTS, TaskStatus.CORE_DOWN,
                        TaskStatus.BAD_INPUT, TaskStatus.UNKNOWN))
                .hasSize(4);
    }
}
