# -*- coding: utf-8 -*-
"""LLM 厂商抽象。多厂商 / 自定义引擎都实现 generate()。
对外（用户/前端）不返回厂商细节 —— 厂商是可替换的一层。
"""
class LLMProvider:
    name = "base"

    def generate(self, system: str, user: str) -> str:
        raise NotImplementedError
