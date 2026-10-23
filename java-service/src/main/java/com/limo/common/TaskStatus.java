package com.limo.common;

/**
 * 任务状态与失败原因的常量。
 *
 * 为什么要有这个类：原先状态是散在各处的字符串字面量（"PENDING"、"DONE"…），
 * 拼错一个字母编译器不管、运行时才炸。收进常量后 IDE 能补全、改名能全局重构。
 *
 * 失败原因**按「用户能采取的行动」划分**，不按「哪一行代码抛的异常」划分——
 * 判据来自同一个作者在 LiMo-C 里踩过的坑（见该项目 docs/patterns.md 第 53 条）：
 * 把超窗、超时、模型报错三种失败混成一个 truncated 标签，用户看哪个标签都无从下手。
 */
public final class TaskStatus {

    private TaskStatus() {}

    // ---- 任务状态 ----
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    // ---- 失败原因（仅 FAILED 时有值）----

    /** 积分不够 → 用户该做的事：充值 / 换套餐 */
    public static final String NO_POINTS = "NO_POINTS";

    /** 处理核心不可用（连不上 / 超时 / 5xx）→ 用户该做的事：稍后重试 */
    public static final String CORE_DOWN = "CORE_DOWN";

    /** 输入有问题（格式不支持 / 链接失效 / 文件损坏）→ 用户该做的事：换一个文件或链接 */
    public static final String BAD_INPUT = "BAD_INPUT";

    /** 其余未分类 -> 用户该做的事：联系我们（并且这条日志里一定有堆栈） */
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * 把异常按「用户能做什么」翻译成失败原因。
     *
     * 只认确定的几种：402/409 是用户该去充值或清理，502 是核心挂了该稍后重试，
     * 400/404 是送过去的东西有问题。其余的（包括 500 这种"我们自己的 bug"）一律 UNKNOWN ——
     * 归错类比不归类更糟：把配置缺失说成"核心不可用，请重试"，用户重试一百次也不会好。
     */
    public static String reasonOf(Throwable e) {
        if (e instanceof BizException be) {
            int code = be.getCode();
            if (code == 402 || code == 409) return NO_POINTS;
            if (code == 502) return CORE_DOWN;
            if (code == 400 || code == 404) return BAD_INPUT;
        }
        return UNKNOWN;
    }
}
