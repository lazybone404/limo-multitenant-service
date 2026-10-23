# -*- coding: utf-8 -*-
"""读取配置：config.yaml 为主，环境变量可覆盖。
引擎/LLM 厂商由服务端配置决定（config.yaml 写死默认，可切换/降级/自定义）。
/capabilities 只返回能力名，不返回引擎/厂商细节 —— 换厂商不该让前端跟着改。
"""
import os
from pathlib import Path
import yaml


def load_config() -> dict:
    cfg = {}
    p = Path(__file__).parent / "config.yaml"
    if p.exists():
        try:
            cfg = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        except Exception:
            cfg = {}

    llm = cfg.get("llm", {}) or {}
    asr = cfg.get("asr", {}) or {}
    engines = cfg.get("engines", {}) or {}
    providers = llm.get("providers", {}) or {}

    provider = os.environ.get("LLM_PROVIDER", llm.get("provider", "openai"))
    prov = providers.get(provider, {}) or {}

    return {
        "llm": {
            "provider": provider,  # 选哪个厂商（服务端可切）
            # 厂商级配置优先，回退到 llm 顶层；环境变量最优先（部署注入 key 最安全）
            "base_url": os.environ.get("LLM_BASE_URL", prov.get("base_url", llm.get("base_url", ""))),
            "api_key": os.environ.get("LLM_API_KEY", prov.get("api_key", llm.get("api_key", ""))),
            "model": os.environ.get("LLM_MODEL", prov.get("model", llm.get("model", "qwen-plus"))),
        },
        "asr": {
            "provider": asr.get("provider", ""),
            "api_key": os.environ.get("ASR_API_KEY", asr.get("api_key", "")),
        },
        "engines": {
            "document": os.environ.get("DOC_ENGINE", engines.get("document", "local")),
        },
        "proxy": os.environ.get("HTTP_PROXY", cfg.get("proxy", "")),
    }
