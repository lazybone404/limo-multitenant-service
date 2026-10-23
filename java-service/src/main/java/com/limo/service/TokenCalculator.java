package com.limo.service;

/**
 * token 核算：根据内容规模估算 LLM 消耗（token），进而折算积分。
 * 1 积分 = 1000 token（可调）。视频按时长、文档按文本量。
 */
public class TokenCalculator {

    /**
     * token → 积分（向上取整）。1 积分 = 1000 token，可在配置中调。
     * 说明：token 本身由核心自报（黑盒、多模态可扩展），java 不再自己估算；这里只做 token→积分 换算。
     */

    /** token → 积分（向上取整） */
    public static int costPoints(int tokens) {
        return (int) Math.ceil(tokens / 1000.0);
    }
}
