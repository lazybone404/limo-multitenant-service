# -*- coding: utf-8 -*-
"""占位厂商：未配 key / 测试时用，链路能通即可（不会暴露给用户）。
真实使用时配 config.yaml 的 provider + key。
"""
from .base import LLMProvider
from .registry import register_provider


import re


def _extract_content(user: str) -> str:
    """从分节 prompt 里只取 <source_content> 正文，避免占位把指令当内容。"""
    m = re.search(r'<source_content>\n(.*?)\n</source_content>', user or '', re.S)
    body = (m.group(1).strip() if m else (user or '')).replace('\n', ' ').strip()
    return body[:500]


@register_provider("mock")
class MockProvider(LLMProvider):
    def generate(self, system: str, user: str) -> str:
        excerpt = _extract_content(user)
        return f"# 笔记\n\n## 核心要点\n- {excerpt}...\n\n## 总结\n- 已将原文提炼为要点版。"
