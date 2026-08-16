import json
import re
from typing import List, Tuple

import httpx

from app.config import settings
from app.errors import AppError
from app.schemas import (
    AIAnalysisBody,
    AIAnalysisContent,
    AIFollowupBody,
    AIUsage,
)
from app.services.case_store import cases_for_hexagram
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


def _field(case: dict, key: str) -> str:
    value = str(case.get(key) or "").strip()
    if not value or value == "原文未提及":
        return ""
    return value


def _cases_block(number: int) -> str:
    cases = cases_for_hexagram(number)
    if not cases:
        return "本卦讲习案例：暂无。"
    lines = [
        f"本卦讲习案例（共{len(cases)}则，按初爻至上爻排列；作取象与应事的参照，须紧扣本次所问与动爻，不可照搬结论）："
    ]
    for index, case in enumerate(cases, start=1):
        hexagram = str(case.get("hexagram") or "")
        position = str(case.get("position") or "")
        parts = [f"【{index}】{hexagram}{position}"]
        for label, key in (
            ("背景", "background"),
            ("所问", "question"),
            ("讲师解读", "explanation"),
            ("验证", "verification"),
        ):
            value = _field(case, key)
            if value:
                parts.append(f"{label}：{value}")
        lines.append("\n".join(parts))
    return "\n\n".join(lines)


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
    parts.append(_cases_block(body.primaryNumber))
    parts.append(
        "解读框架：卦辞看事情背景；大象辞看占者宜努力的方向；动爻之爻辞与小象看当下情形"
        "（无动爻时，当下参本卦卦辞与大象）。可参照讲习案例的取象方式，但结论必须针对本次所问。"
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


def _parse_json_object(content: str) -> dict:
    text = content.strip()
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if fence:
        text = fence.group(1).strip()
    data = json.loads(text)
    if not isinstance(data, dict):
        raise json.JSONDecodeError("not an object", text, 0)
    return data


def _complete_json(system_prompt: str, user_prompt: str) -> Tuple[dict, AIUsage]:
    if not settings.openai_api_key:
        raise AppError("未配置 OPENAI_API_KEY", code=5000, status_code=500)

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
        parsed = _parse_json_object(content)
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise AppError("模型返回格式异常", code=5000, status_code=502) from exc
    return parsed, usage


def _analyze_openai(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    system_prompt = (
        "你是「易知道」的易经解读助手。依据提供的卦象与经文（《易经证释》所引）做占问解读。"
        "解读须按：卦辞→事情背景；大象辞→占者宜努力的方向；动爻之爻辞与小象→当下情形。"
        "结论从卦象、卦辞、大象辞、爻辞、小象辞中归纳，紧扣「解卦焦点」；可简要参彖辞。"
        "另附该本卦初爻至上爻的讲习案例，可参照其取象、应事与验证，但必须针对本次所问与动爻，"
        "不可把案例原事或结论直接套到用户身上。"
        "只输出 JSON，不要 markdown，格式："
        '{"summary":"总览：据卦辞交代事情背景1-2句","focus":"当下：据动爻爻辞/小象说当下情形1句","advice":["方向：据大象辞的努力方向","建议2","建议3"]}'
    )
    parsed, usage = _complete_json(system_prompt, _build_prompt(body))
    advice = parsed.get("advice") or []
    if isinstance(advice, str):
        advice = [advice]
    analysis = AIAnalysisContent(
        summary=str(parsed.get("summary", "")).strip(),
        focus=str(parsed.get("focus", "")).strip(),
        advice=[str(item).strip() for item in advice if str(item).strip()],
    )
    if not analysis.summary or not analysis.focus or not analysis.advice:
        raise AppError("模型返回内容不完整", code=5000, status_code=502)
    return analysis, usage


def _followup_prompt(body: AIFollowupBody) -> str:
    prev = body.previousAnalysis
    advice = "；".join(prev.advice)
    parts = [
        _build_prompt(body),
        "此前解读：",
        f"总览：{prev.summary}",
        f"焦点：{prev.focus}",
        f"建议：{advice}",
    ]
    if body.conversation:
        parts.append("此前追问：")
        for index, turn in enumerate(body.conversation[-10:], start=1):
            parts.append(f"{index}. 用户：{turn.user}\n   助手：{turn.assistant}")
    parts.append(f"用户最新追问或补充：{body.message.strip()}")
    parts.append("请针对这条追问/补充作答，可修正或细化此前解读，不要重复粘贴经文。")
    return "\n\n".join(parts)


def _followup_mock(body: AIFollowupBody) -> Tuple[str, AIUsage]:
    text = body.message.strip()
    clipped = text if len(text) <= 40 else text[:40] + "…"
    reply = f"已记下你的补充「{clipped}」。请仍对照本卦动爻与此前解读，把新背景收进判断里。"
    return reply, AIUsage(promptTokens=0, completionTokens=0)


def _followup_openai(body: AIFollowupBody) -> Tuple[str, AIUsage]:
    system_prompt = (
        "你是「易知道」的易经解读助手。用户已得到初次解读，现在追问或补充背景。"
        "结合卦象、经文、讲习案例与此前解读作答；有新背景时据此调整判断。"
        "不可把讲习案例原事直接套到用户身上。不要重复堆砌经文。"
        "只输出 JSON，不要 markdown，格式："
        '{"reply":"针对追问的答复，可分2-6句"}'
    )
    parsed, usage = _complete_json(system_prompt, _followup_prompt(body))
    reply = str(parsed.get("reply", "")).strip()
    if not reply:
        raise AppError("模型返回内容不完整", code=5000, status_code=502)
    return reply, usage


def analyze_reading(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    mode = (settings.ai_mode or "mock").lower()
    if mode == "openai":
        return _analyze_openai(body)
    return _analyze_mock(body)


def followup_reading(body: AIFollowupBody) -> Tuple[str, AIUsage]:
    mode = (settings.ai_mode or "mock").lower()
    if mode == "openai":
        return _followup_openai(body)
    return _followup_mock(body)
