# -*- coding: utf-8 -*-
"""OpenAI 兼容厂商（默认）—— 支持 DashScope / 任意兼容 / 你们自己的服务。
base_url/model/key 从 config.py 读（可在 config.yaml 或环境变量配多厂商）。
"""
from openai import OpenAI
from config import load_config
from .base import LLMProvider
from .registry import register_provider


@register_provider("openai")
class OpenAICompatProvider(LLMProvider):
    def generate(self, system: str, user: str) -> str:
        cfg = load_config().get("llm", {})
        client = OpenAI(base_url=cfg.get("base_url") or None, api_key=cfg.get("api_key"))
        resp = client.chat.completions.create(
            model=cfg.get("model", "qwen-plus"),
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            temperature=0.3,
        )
        return resp.choices[0].message.content
