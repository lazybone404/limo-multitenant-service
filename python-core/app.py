# -*- coding: utf-8 -*-
"""limo python-core 服务（供 Java 调用）。
内部：引擎可插拔（engines/）+ LLM 厂商可插拔（llm/）+ 按类型/风格注入提示词（generation/）
     ——都由服务端配置决定。
对外：只暴露【能力】，不暴露引擎/厂商/风格 —— /capabilities 只返回能力名。
支持入参：上传文件(multipart) / 文本 / url。
"""
import tempfile
import shutil
from pathlib import Path
from fastapi import FastAPI, Request, HTTPException

from engines import get_engine
from generate import generate_note
from config import load_config

app = FastAPI(title="limo-python-core", description="视频/文档 → 笔记（供 Java 调用）")


def _extract(path: str) -> str:
    cfg = load_config().get("engines", {})
    return get_engine("document", cfg.get("document", "local")).extract(path)


@app.post("/convert")
async def convert(request: Request):
    """把内容转成笔记。json: {type,url?,text?,style?,options?}；或 multipart: file + type。"""
    ct = request.headers.get("content-type", "")
    tmp_dir = None
    try:
        ctype = "document"
        style = None
        options = None
        if "multipart/form-data" in ct:
            form = await request.form()
            ctype = form.get("type", "document")
            f = form.get("file")
            if f is None:
                raise HTTPException(400, "请上传文件")
            tmp_dir = tempfile.mkdtemp(prefix="limo_up_")
            path = Path(tmp_dir) / (f.filename or "doc")
            path.write_bytes(await f.read())
            raw_text = _extract(str(path))
        else:
            body = await request.json()
            ctype = body.get("type", "document")
            url = body.get("url", "")
            text = body.get("text", "")
            style = body.get("style")
            options = body.get("options")
            if ctype == "video":
                raise HTTPException(503, "视频转笔记需配置 ASR（见 config.yaml），MVP 先支持文档")
            if url:
                raw_text = _extract_from_url(url)
            elif text:
                raw_text = text
            else:
                raise HTTPException(400, "请提供文件 / url / 文本")

        # 风格/选项：config 给默认值，json 可以覆盖。前端能传，服务端也能改默认
        note = generate_note(raw_text, ctype, style=style, options=options)
        tokens = len(raw_text) // 4 if ctype != "video" else 0
        return {
            "note": note,
            "raw_text": raw_text[:3000],
            "duration": 0,
            "usage": {"tokens": tokens, "cost": tokens},
        }
    finally:
        if tmp_dir:
            shutil.rmtree(tmp_dir, ignore_errors=True)


def _extract_from_url(url: str) -> str:
    """下载 url 指向的文档 → 用引擎提取文字。"""
    import httpx
    from urllib.parse import urlparse
    tmp_dir = tempfile.mkdtemp(prefix="limo_url_")
    try:
        fname = Path(urlparse(url).path).name or "download"
        dest = Path(tmp_dir) / fname
        with httpx.Client(follow_redirects=True, timeout=60) as c:
            dest.write_bytes(c.get(url).content)
        return _extract(str(dest))
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


@app.get("/capabilities")
async def capabilities():
    """对外只返回能力名，不返回引擎/厂商/风格 —— 换一家厂商，前端不需要跟着改。"""
    return {"capabilities": [
        {"name": "document", "label": "文档转笔记"},
        {"name": "video", "label": "视频转笔记"},
    ]}


@app.post("/estimate")
async def estimate(request: Request):
    """预估本次转换要消耗的 token（提交时预扣依据；核心黑盒自己估）。"""
    body = await request.json()
    type_ = body.get("type", "document")
    url = body.get("url", "")
    text = body.get("text", "")
    if type_ == "video":
        est = 10 * 1000
    elif url:
        est = 5 * 1000
    else:
        est = max(1, len(text) // 4)
    return {"tokens": est, "cost": est}


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
