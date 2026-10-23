# -*- coding: utf-8 -*-
"""本地提取引擎：用本机 lib 提取文档文字（提取逻辑在 extract/document.py）。
这是默认的 document 引擎。加新引擎只需新增文件 + @register_engine。
"""
from extract.document import extract_document
from .base import BaseExtractor
from .registry import register_engine


@register_engine("document", "local")
class LocalExtractor(BaseExtractor):
    def extract(self, path: str) -> str:
        return extract_document(path)
