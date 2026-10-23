# -*- coding: utf-8 -*-
"""生成器：构建 prompt → 选 LLM 厂商 → 生成笔记。
风格/选项由 config 控制，可被 convert 覆盖。
"""
from config import load_config
from llm import get_provider

from . import prompt_builder


def generate(source_type: str, text: str, style: str | None = None,
             options: dict | None = None, title: str = "") -> str:
    cfg = load_config()
    gen = cfg.get("generation", {}) or {}
    style = style or gen.get("style", "detailed")
    options = options or gen.get("options", {}) or {}

    prompt = prompt_builder.build(source_type, text, title=title, style=style, options=options)
    system = "严格遵循下方要求输出 Markdown 笔记。不要输出标签本身。"

    llm = cfg.get("llm", {})
    provider_name = llm.get("provider", "openai")
    if provider_name == "mock" or not llm.get("api_key"):
        return get_provider("mock").generate(system, prompt)
    return get_provider(provider_name).generate(system, prompt)
