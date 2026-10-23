# -*- coding: utf-8 -*-
"""按来源类型 + 风格构建 prompt（注入角色/任务/风格/格式/内容分节）。"""
from .prompts.common import PromptComposer
from .prompts.notes_styles import get_styles

ROLE = "你是一个专业的学习笔记助手，擅长把各种内容提炼成结构化的 Markdown 笔记。"
TASK = "把用户提供的内容转成结构化 Markdown 笔记：包含【核心要点】【详细内容】【总结】。忠实原文，不要编造。"


def build(source_type: str, text: str, title: str = "", style: str = "detailed",
          options: dict | None = None) -> str:
    style_text = get_styles(source_type).get(style) or get_styles(source_type)["detailed"]
    return PromptComposer.compose([
        ("role", ROLE),
        ("task", TASK),
        ("style_preference", style_text),
        ("output_format", "使用 Markdown；标题层级清晰；如需目录/摘要，按 options 生成"),
        ("source_content", text),
    ])
