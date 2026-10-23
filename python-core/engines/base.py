# -*- coding: utf-8 -*-
"""处理引擎抽象。每种能力（document/video...）可有多引擎，config 选哪个。"""
class BaseExtractor:
    name = "base"

    def extract(self, path: str) -> str:
        raise NotImplementedError
