# -*- coding: utf-8 -*-
"""视频转笔记（预留）：下载/字幕 → ASR 转写 → 得到 raw_text（带时间轴）。
MVP 先做【文档转笔记】；视频需要配置 ASR（dashscope/bcut，见 config.yaml + ASR_API_KEY）。
依赖：asr 引擎的 api_key。等配好 ASR 后再启用视频链路。
"""

def extract_video(video_path_or_url: str, asr_cfg: dict) -> dict:
    """返回 { raw_text, segments, duration } 或抛异常。MVP 未启用。"""
    raise NotImplementedError(
        "视频转笔记需配置 ASR（dashscope/bcut），MVP 阶段先只支持文档转笔记"
    )
