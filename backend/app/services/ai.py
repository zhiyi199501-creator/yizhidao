import json
import re
from typing import List, Tuple

import httpx

from app.config import settings
from app.errors import AppError
from app.schemas import AIAnalysisBody, AIAnalysisContent, AIUsage
from app.services.hexagram_store import get_hexagram


def _yao_name(position: int) -> str:
    names = {1: "初", 2: "二", 3: "三", 4: "四", 5: "五", 6: "上"}
    return names.get(position, str(position))


def _focus_text(moving: List[int]) -> str:
    count = len(moving)
    if count == 0:
        return "六爻皆不变，主看本卦卦辞。"
    if count == 1:
        return f"一爻变，主看本卦{_yao_name(moving[0])}爻辞。"
    if count == 2:
        lead = moving[-1]
        return f"二爻变，主看本卦两动爻爻辞，以{_yao_name(lead)}爻为主。"
    if count == 3:
        return "三爻变，主看本卦、之卦卦辞，以本卦为主。"
    if count == 4:
        statics = [p for p in range(1, 7) if p not in moving]
        lead = statics[0] if statics else 1
        return f"四爻变，主看之卦两静爻爻辞，以{_yao_name(lead)}爻为主。"
    if count == 5:
        statics = [p for p in range(1, 7) if p not in moving]
        lead = statics[0] if statics else 1
        return f"五爻变，主看之卦静爻{_yao_name(lead)}爻辞。"
    return "六爻皆变，主看之卦卦辞。"


def _method_label(method: str) -> str:
    mapping = {
        "digitalManual": "数字起卦·三数",
        "digitalTime": "数字起卦·时间",
        "coin": "六爻金钱卦",
    }
    return mapping.get(method, method)


def _hex_block(number: int, label: str) -> str:
    hexagram = get_hexagram(number)
    if not hexagram:
        return f"{label}：第{number}卦（经文未加载）"
    yaoci = "\n".join(f"  {i + 1}. {line}" for i, line in enumerate(hexagram.get("yaoci", [])))
    xiaoxiang = "\n".join(f"  {i + 1}. {line}" for i, line in enumerate(hexagram.get("xiaoxiang", [])))
    return (
        f"{label}：{hexagram.get('symbol', '')} {hexagram.get('name', '')}（第{number}卦）\n"
        f"卦辞：{hexagram.get('guaci', '')}\n"
        f"大象：{hexagram.get('daxiang', '')}\n"
        f"爻辞：\n{yaoci}\n"
        f"小象辞：\n{xiaoxiang}"
    )


def _build_prompt(body: AIAnalysisBody) -> str:
    moving = sorted(set(p for p in body.movingPositions if 1 <= p <= 6))
    focus = _focus_text(moving)
    question = (body.question or "").strip() or "（用户未填写所问）"

    parts = [
        f"起卦方式：{_method_label(body.method)}",
        f"所问：{question}",
        _hex_block(body.primaryNumber, "本卦"),
    ]
    if body.resultingNumber:
        parts.append(_hex_block(body.resultingNumber, "之卦"))
    if moving:
        parts.append(f"动爻位（1=初爻）：{', '.join(_yao_name(p) for p in moving)}")
    else:
        parts.append("动爻位：无（六爻皆不变）")
    parts.append(f"解卦焦点：{focus}")
    parts.append(
        "解读框架：卦辞看事情背景；大象辞看占者宜努力的方向；动爻之爻辞与小象看当下情形"
        "（无动爻时，当下参本卦卦辞与大象）。"
    )
    return "\n\n".join(parts)


def _analyze_mock(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    moving = sorted(set(p for p in body.movingPositions if 1 <= p <= 6))
    focus = _focus_text(moving)
    question = (body.question or "").strip()
    if question:
        summary = f"就「{question}」而言，宜先辨本卦与动变之消息，再按解卦通则取舍。"
    else:
        summary = "所问未明，宜先回观本卦与动变之消息，再按解卦通则取舍。"

    advice = [
        "本卦为目前情状，之卦为将来趋势，勿只看其一。",
        focus.rstrip("。"),
        "占得仅供参考，关键仍在于审时度势、守中而行。",
    ]
    if body.resultingNumber is None and moving:
        advice.insert(1, "动爻已显，宜细玩动爻辞义，不必急求之卦。")

    return AIAnalysisContent(summary=summary, focus=focus, advice=advice), AIUsage(
        promptTokens=0, completionTokens=0
    )


def _parse_model_json(content: str) -> AIAnalysisContent:
    text = content.strip()
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if fence:
        text = fence.group(1).strip()
    data = json.loads(text)
    advice = data.get("advice") or []
    if isinstance(advice, str):
        advice = [advice]
    return AIAnalysisContent(
        summary=str(data.get("summary", "")).strip(),
        focus=str(data.get("focus", "")).strip(),
        advice=[str(item).strip() for item in advice if str(item).strip()],
    )


def _analyze_openai(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    if not settings.openai_api_key:
        raise AppError("未配置 OPENAI_API_KEY", code=5000, status_code=500)

    system_prompt = (
        "你是「易知道」的易经解读助手。依据提供的卦象与经文（《易经证释》所引）做占问解读。"
        "解读须按：卦辞→事情背景；大象辞→占者宜努力的方向；动爻之爻辞与小象→当下情形。"
        "结论从卦象、卦辞、大象辞、爻辞、小象辞中归纳，紧扣「解卦焦点」；可简要参彖辞。"
        "只输出 JSON，不要 markdown，格式："
        '{"summary":"总览：据卦辞交代事情背景1-2句","focus":"当下：据动爻爻辞/小象说当下情形1句","advice":["方向：据大象辞的努力方向","建议2","建议3"]}'
    )
    user_prompt = _build_prompt(body)

    url = f"{settings.openai_base_url.rstrip('/')}/chat/completions"
    payload = {
        "model": settings.openai_model,
        "temperature": settings.openai_temperature,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "response_format": {"type": "json_object"},
    }
    headers = {
        "Authorization": f"Bearer {settings.openai_api_key}",
        "Content-Type": "application/json",
    }

    try:
        with httpx.Client(timeout=settings.openai_timeout_sec) as client:
            resp = client.post(url, headers=headers, json=payload)
    except httpx.HTTPError as exc:
        raise AppError("模型服务连接失败", code=5000, status_code=502) from exc

    if resp.status_code >= 400:
        detail = resp.text[:200]
        raise AppError(f"模型调用失败：{detail}", code=5000, status_code=502)

    data = resp.json()
    content = data["choices"][0]["message"]["content"]
    usage_raw = data.get("usage") or {}
    usage = AIUsage(
        promptTokens=int(usage_raw.get("prompt_tokens", 0)),
        completionTokens=int(usage_raw.get("completion_tokens", 0)),
    )

    try:
        analysis = _parse_model_json(content)
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise AppError("模型返回格式异常", code=5000, status_code=502) from exc

    if not analysis.summary or not analysis.focus or not analysis.advice:
        raise AppError("模型返回内容不完整", code=5000, status_code=502)

    return analysis, usage


def analyze_reading(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    mode = (settings.ai_mode or "mock").lower()
    if mode == "openai":
        return _analyze_openai(body)
    return _analyze_mock(body)
