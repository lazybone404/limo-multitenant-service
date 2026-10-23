# -*- coding: utf-8 -*-
"""生成入口：兼容旧调用，委托给 generation.generator（按类型/风格注入提示词）。"""
from generation.generator import generate


def generate_note(raw_text: str, note_type: str = "document",
                  style: str | None = None, options: dict | None = None) -> str:
    return generate(note_type, raw_text, style=style, options=options)
