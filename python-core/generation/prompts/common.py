# -*- coding: utf-8 -*-
"""PromptComposer —— 分节组装 prompt，用标签包裹，空节跳过。
（对齐 C 端 generation/prompts/common.py 的分节思想）
标签语义：<role>/<task>/<style_preference>/<output_format>/<source_content> 等。
"""
class PromptComposer:
    @staticmethod
    def section(name: str, text: str) -> str:
        text = (text or "").strip()
        return "" if not text else f"<{name}>\n{text}\n</{name}>"

    @staticmethod
    def compose(sections: list[tuple[str, str]]) -> str:
        parts = [PromptComposer.section(n, t) for n, t in sections]
        return "\n\n".join(p for p in parts if p)
