# -*- coding: utf-8 -*-
"""笔记风格集 —— 视频和文档各自独立的风格集（对齐 C 端 notes_styles.py 思想）。
- COMMON：视频/文档共享基础风格
- VIDEO：视频独有（minimal/meeting…）
- DOC：文档独有（outline/report…）
不同 type 注入不同风格，体现"不同类型不同提示词注入与选项"。
"""
_STYLE_HEADER = "## 输出风格\n"

COMMON: dict[str, str] = {
    "detailed": _STYLE_HEADER + "\n".join([
        "**详细模式**：",
        "- 尽可能覆盖原文全部主要内容",
        "- 保留重要的事实、数据、例子和论证过程",
        "- 每个话题充分展开，不因篇幅删减有价值的信息",
    ]),
    "concise": _STYLE_HEADER + "\n".join([
        "**精简模式**：",
        "- 只记核心论点和关键结论，大胆省略细节",
        "- 倾向短列表而非长段落，简洁优先",
    ]),
    "academic": _STYLE_HEADER + "\n".join([
        "**学术风格**：",
        "- 正式学术用语，结构清晰严谨",
        "- 按 背景→方法→论证→结论 组织",
        "- 保留专业术语、引用和理论框架",
    ]),
    "tutorial": _STYLE_HEADER + "\n".join([
        "**教程笔记**：",
        "- 按操作顺序组织，步骤清晰",
        "- 关键步骤写具体命令/配置/参数",
        "- 补充上下文（为什么这样做、常见错误）",
        "- 代码、公式、关键数值必须保留",
    ]),
}

VIDEO: dict[str, str] = {
    **COMMON,
    "meeting": _STYLE_HEADER + "\n".join([
        "**会议纪要**：",
        "- 按议题归类讨论内容",
        "- 明确标注决议和待办事项",
    ]),
}

DOC: dict[str, str] = {
    **COMMON,
    "outline": _STYLE_HEADER + "\n".join([
        "**大纲式**：",
        "- 按层级提炼结构，保留要点骨架",
        "- 细节可折叠，突出主线",
    ]),
}


def get_styles(source_type: str) -> dict[str, str]:
    """按来源类型返回风格集（独立集，不相互污染）。"""
    return VIDEO if source_type == "video" else DOC
