# -*- coding: utf-8 -*-
"""引擎注册表：新引擎加一个实现类 + @register_engine(capability, name) 即可。
config.yaml 的 engines.<capability> 指定默认引擎，换引擎不用改调用方。
"""
from .base import BaseExtractor

_REG: dict[str, type[BaseExtractor]] = {}


def register_engine(capability: str, name: str):
    """装饰器：把引擎实现注册到能力名下。"""
    def deco(cls):
        _REG[f"{capability}:{name}"] = cls
        cls.name = name
        return cls
    return deco


def get_engine(capability: str, name: str = "local") -> BaseExtractor:
    cls = _REG.get(f"{capability}:{name}") or _REG.get(f"{capability}:local")
    if cls is None:
        raise RuntimeError(f"no engine for {capability} / {name}")
    return cls()


def list_engines(capability: str):
    return [k.split(":", 1)[1] for k in _REG if k.startswith(capability + ":")]


from . import local  # noqa: E402,F401  （触发注册）
