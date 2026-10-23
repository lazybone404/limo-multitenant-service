# -*- coding: utf-8 -*-
"""文档提取：PDF / Word / PPT / Excel / EPUB → 纯文本。
全部用本地库（无需 API key），是文档转笔记的第一步（拿到 raw_text 再喂给 LLM）。
"""
import io
from pathlib import Path

def extract_document(path: str) -> str:
    """按文件扩展名提取文字，返回 Markdown 文本。"""
    ext = Path(path).suffix.lower()
    if ext == ".pdf":
        return _extract_pdf(path)
    if ext in (".docx", ".doc"):
        return _extract_docx(path)
    if ext in (".pptx", ".ppt"):
        return _extract_pptx(path)
    if ext in (".xlsx", ".xls"):
        return _extract_xlsx(path)
    if ext == ".epub":
        return _extract_epub(path)
    if ext in (".txt", ".md", ".markdown"):
        # 纯文本 / 纯 Markdown：直接读取
        return Path(path).read_text(encoding="utf-8", errors="ignore")
    raise ValueError(f"不支持的文档类型: {ext}")

def _extract_pdf(path: str) -> str:
    import pypdfium2 as pdfium
    pdf = pdfium.PdfDocument(path)
    parts = []
    for i in range(len(pdf)):
        page = pdf[i]
        text = page.get_textpage().get_text_bounded()
        parts.append(f"## 第{i+1}页\n\n{text.strip()}" if text.strip() else f"## 第{i+1}页\n_(无文字层，可能是扫描件)_")
    pdf.close()
    return "\n\n".join(parts)

def _extract_docx(path: str) -> str:
    import docx
    d = docx.Document(path)
    return "\n".join(p.text for p in d.paragraphs if p.text.strip())

def _extract_pptx(path: str) -> str:
    from pptx import Presentation
    prs = Presentation(path)
    parts = []
    for i, slide in enumerate(prs.slides, 1):
        texts = [shape.text for shape in slide.shapes if hasattr(shape, "text") and shape.text.strip()]
        parts.append(f"## 第{i}页\n\n" + "\n".join(texts))
    return "\n\n".join(parts)

def _extract_xlsx(path: str) -> str:
    import openpyxl
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    out = []
    for ws in wb.worksheets:
        out.append(f"## 工作表: {ws.title}")
        for row in ws.iter_rows(values_only=True):
            vals = [str(c) for c in row if c is not None and str(c).strip()]
            if vals:
                out.append(" | ".join(vals))
    wb.close()
    return "\n".join(out)

def _extract_epub(path: str) -> str:
    import ebooklib
    from ebooklib import epub
    from bs4 import BeautifulSoup
    book = epub.read_epub(path)
    parts = []
    for doc in book.get_items_of_type(ebooklib.ITEM_DOCUMENT):
        soup = BeautifulSoup(doc.get_content(), "html.parser")
        for p in soup.find_all("p"):
            if p.get_text().strip():
                parts.append(p.get_text().strip())
    return "\n".join(parts)
