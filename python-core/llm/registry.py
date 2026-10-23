# -*- coding: utf-8 -*-
"""LLM 厂商注册表：新厂商/自定义引擎只需加一个实现类 + @register_provider 即可。
config.yaml 选 provider。
"""
from .base import LLMProvider

_REG: dict[str, type[LLMProvider]] = {}


def register_provider(name: str):
    """装饰器：把一个 LLMProvider 实现类注册进表，name 是厂商标识。"""
    def deco(cls):
        _REG[name] = cls
        cls.name = name
        return cls
    return deco


def get_provider(name: str) -> LLMProvider:
    """按配置的厂商名取实例；未知则回落 openai。"""
    cls = _REG.get(name) or _REG.get("openai")
    if cls is None:
        raise RuntimeError("no LLM provider registered")
    return cls()


def list_providers():
    return list(_REG)


# 自动引入内置厂商，触发注册
from . import openai_compat  # noqa: E402,F401
from . import mock           # noqa: E402,F401
