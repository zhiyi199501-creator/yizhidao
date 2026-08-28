import json
import re
from typing import List, Optional, Tuple

import httpx

from app.config import settings
from app.errors import AppError
from app.schemas import (
    AIAnalysisBody,
    AIAnalysisContent,
    AIFollowupBody,
    AIUsage,
)
from app.services.case_store import cases_for_ai_prompt
from app.services.hexagram_store import get_hexagram
from app.services.ima_store import formatted_answer, get_entry


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


def _hex_name_line(number: int, label: str) -> str:
    hexagram = get_hexagram(number)
    if not hexagram:
        return f"{label}：第{number}卦"
    return f"{label}：{hexagram.get('symbol', '')} {hexagram.get('name', '')}（第{number}卦）"


def _line_at(hexagram: dict, position: int, key: str) -> str:
    items = hexagram.get(key) or []
    index = position - 1
    if 0 <= index < len(items):
        return str(items[index]).strip()
    return ""


def _yao_snippet(number: int, position: int, label: str, lead: bool = False) -> str:
    hexagram = get_hexagram(number)
    mark = "（主）" if lead else ""
    if not hexagram:
        return f"{label}{_yao_name(position)}爻{mark}：经文未加载"
    ci = _line_at(hexagram, position, "yaoci")
    xiang = _line_at(hexagram, position, "xiaoxiang")
    head = f"{label}{_yao_name(position)}爻{mark}：{ci}" if ci else f"{label}{_yao_name(position)}爻{mark}"
    if xiang:
        return f"{head}\n小象：{xiang}"
    return head


def _guaci_snippet(number: int, label: str, with_daxiang: bool = False) -> str:
    hexagram = get_hexagram(number)
    if not hexagram:
        return f"{label}：第{number}卦（经文未加载）"
    lines = [f"{label}卦辞：{hexagram.get('guaci') or ''}"]
    tuanci = str(hexagram.get("tuanci") or "").strip()
    if tuanci:
        lines.append(f"{label}彖辞：{tuanci}")
    if with_daxiang:
        daxiang = str(hexagram.get("daxiang") or "").strip()
        if daxiang:
            lines.append(f"{label}大象：{daxiang}")
    return "\n".join(lines)


def _focus_scripture_block(
    primary_number: int,
    resulting_number: Optional[int],
    moving: List[int],
) -> str:
    """追问只用焦点经文原文，不附黄庭、不附其余爻。"""
    moving = sorted(set(p for p in moving if 1 <= p <= 6))
    count = len(moving)
    lines = ["焦点经文："]
    if count == 0:
        lines.append(_guaci_snippet(primary_number, "本卦", with_daxiang=True))
    elif count == 1:
        lines.append(_yao_snippet(primary_number, moving[0], "本卦"))
    elif count == 2:
        lead = moving[-1]
        for pos in moving:
            lines.append(_yao_snippet(primary_number, pos, "本卦", lead=pos == lead))
    elif count == 3:
        lines.append(_guaci_snippet(primary_number, "本卦", with_daxiang=True))
        if resulting_number:
            lines.append(_guaci_snippet(resulting_number, "之卦"))
    elif count == 4 and resulting_number:
        statics = [p for p in range(1, 7) if p not in moving]
        lead = statics[0] if statics else 1
        for pos in statics:
            lines.append(_yao_snippet(resulting_number, pos, "之卦", lead=pos == lead))
    elif count == 5 and resulting_number:
        statics = [p for p in range(1, 7) if p not in moving]
        pos = statics[0] if statics else 1
        lines.append(_yao_snippet(resulting_number, pos, "之卦"))
    elif count == 6 and resulting_number:
        lines.append(_guaci_snippet(resulting_number, "之卦", with_daxiang=True))
    return "\n".join(lines)


def _reading_sketch(body: AIAnalysisBody) -> str:
    moving = sorted(set(p for p in body.movingPositions if 1 <= p <= 6))
    question = (body.question or "").strip() or "（用户未填写所问）"
    parts = [
        f"起卦方式：{_method_label(body.method)}",
        f"所问：{question}",
        _hex_name_line(body.primaryNumber, "本卦"),
    ]
    if body.resultingNumber:
        parts.append(_hex_name_line(body.resultingNumber, "之卦"))
    if moving:
        parts.append(f"动爻位（1=初爻）：{', '.join(_yao_name(p) for p in moving)}")
    else:
        parts.append("动爻位：无（六爻皆不变）")
    parts.append(f"解卦焦点：{_focus_text(moving)}")
    parts.append(_focus_scripture_block(body.primaryNumber, body.resultingNumber, moving))
    return "\n".join(parts)


def _hex_block(number: int, label: str) -> str:
    hexagram = get_hexagram(number)
    if not hexagram:
        return f"{label}：第{number}卦（经文未加载）"
    yaoci = "\n".join(f"  {i + 1}. {line}" for i, line in enumerate(hexagram.get("yaoci", [])))
    xiaoxiang = "\n".join(f"  {i + 1}. {line}" for i, line in enumerate(hexagram.get("xiaoxiang", [])))
    tuanci = str(hexagram.get("tuanci") or "").strip()
    tuan_line = f"彖辞：{tuanci}\n" if tuanci else ""
    return (
        f"{label}：{hexagram.get('symbol', '')} {hexagram.get('name', '')}（第{number}卦）\n"
        f"卦辞：{hexagram.get('guaci', '')}\n"
        f"{tuan_line}"
        f"大象：{hexagram.get('daxiang', '')}\n"
        f"爻辞：\n{yaoci}\n"
        f"小象辞：\n{xiaoxiang}"
    )


def _field(case: dict, key: str) -> str:
    value = str(case.get(key) or "").strip()
    if not value or value == "原文未提及":
        return ""
    return value


def _ima_id_guaci(number: int) -> str:
    return f"{number:02d}-guaci"


def _ima_id_daxiang(number: int) -> str:
    return f"{number:02d}-daxiang"


def _ima_id_tuanci(number: int) -> str:
    return f"{number:02d}-tuanci"


def _ima_id_yao(number: int, position: int) -> str:
    return f"{number:02d}-yao-{position - 1}"


def explanation_slots(
    primary_number: int,
    resulting_number: Optional[int],
    moving: List[int],
) -> List[Tuple[str, str]]:
    """按解卦通则选出要喂给模型的讲解：(角色说明, IMA id)。

    本卦卦辞始终作为大背景。黄庭彖辞只在主看卦辞时注入（0/3 动本卦，6 动之卦），
    不灌文言、用九用六。
    """
    moving = sorted(set(p for p in moving if 1 <= p <= 6))
    slots: List[Tuple[str, str]] = []
    seen = set()

    def add(role: str, entry_id: str) -> None:
        if entry_id in seen:
            return
        seen.add(entry_id)
        slots.append((role, entry_id))

    add("本卦卦辞·事情大背景", _ima_id_guaci(primary_number))
    count = len(moving)
    if count in (0, 3):
        add("本卦彖辞·卦辞格局", _ima_id_tuanci(primary_number))
    add("本卦大象·努力方向", _ima_id_daxiang(primary_number))

    if count == 1:
        pos = moving[0]
        add(f"本卦{_yao_name(pos)}爻·当下", _ima_id_yao(primary_number, pos))
    elif count == 2:
        lead = moving[-1]
        for pos in moving:
            mark = "（主）" if pos == lead else ""
            add(f"本卦{_yao_name(pos)}爻·当下{mark}", _ima_id_yao(primary_number, pos))
    elif count == 3:
        if resulting_number:
            add("之卦卦辞·变后背景", _ima_id_guaci(resulting_number))
    elif count == 4:
        if resulting_number:
            statics = [p for p in range(1, 7) if p not in moving]
            lead = statics[0] if statics else 1
            for pos in statics:
                mark = "（主）" if pos == lead else ""
                add(f"之卦{_yao_name(pos)}爻·当下{mark}", _ima_id_yao(resulting_number, pos))
    elif count == 5:
        if resulting_number:
            statics = [p for p in range(1, 7) if p not in moving]
            pos = statics[0] if statics else 1
            add(f"之卦{_yao_name(pos)}爻·当下", _ima_id_yao(resulting_number, pos))
    elif count == 6:
        if resulting_number:
            add("之卦卦辞·事情归宿", _ima_id_guaci(resulting_number))
            add("之卦彖辞·卦辞格局", _ima_id_tuanci(resulting_number))
            add("之卦大象·努力方向", _ima_id_daxiang(resulting_number))
    return slots


def _explanations_block(
    primary_number: int,
    resulting_number: Optional[int],
    moving: List[int],
) -> str:
    header = (
        "黄庭书院经文讲解（本卦卦辞为事情大背景；主看卦辞时附彖辞以明格局；"
        "大象与焦点爻辞帮助理解辞义。彖与卦辞讲解勿写成两套背景。"
        "须消化后针对本次所问归纳，不可整段照抄）："
    )
    sections = []
    for role, entry_id in explanation_slots(primary_number, resulting_number, moving):
        entry = get_entry(entry_id)
        if not entry:
            continue
        title = str(entry.get("title") or "").strip()
        scripture = str(entry.get("scripture") or "").strip()
        answer = formatted_answer(entry)
        if not answer:
            continue
        parts = [f"【{role}】{title}"]
        if scripture:
            parts.append(f"经文：{scripture}")
        parts.append(f"讲解：{answer}")
        sections.append("\n".join(parts))
    if not sections:
        return "黄庭书院经文讲解：暂无。"
    return header + "\n\n" + "\n\n".join(sections)


def _cases_block(
    primary_number: int,
    resulting_number: Optional[int],
    moving: List[int],
) -> str:
    caption, cases = cases_for_ai_prompt(primary_number, resulting_number, moving)
    if not cases:
        return caption
    lines = [caption]
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
    parts.append(_explanations_block(body.primaryNumber, body.resultingNumber, moving))
    parts.append(_cases_block(body.primaryNumber, body.resultingNumber, moving))
    parts.append(
        "解读框架：卦辞看事情背景（先参本卦卦辞讲解，彖辞助理解格局，勿另起一套背景）；"
        "大象辞看占者宜努力的方向；动爻之爻辞与小象看当下情形（无动爻时，当下参本卦卦辞与大象）。"
        "黄庭讲解用于理解辞义；讲习案例若附上则只作取象参照。结论必须针对本次所问。"
        "请分别写清事情背景、当下、方向、须防（一条）与建议，并给出用户可直接点选发出的短追问（用「我」的口吻）。"
        "若用户未填写所问：建议里请其点选事业或感情方向；可再问由系统固定，不必自拟。"
    )
    return "\n\n".join(parts)


def _question_blank(question: Optional[str]) -> bool:
    return not (question or "").strip()


EMPTY_QUESTION_ASK_NEXT = ["我的事业会如何？", "我的感情会如何？"]


def _apply_empty_question_ask_next(question: Optional[str], analysis: AIAnalysisContent) -> AIAnalysisContent:
    if _question_blank(question):
        analysis.askNext = list(EMPTY_QUESTION_ASK_NEXT)
    return analysis


def _analyze_mock(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    moving = sorted(set(p for p in body.movingPositions if 1 <= p <= 6))
    focus = _focus_text(moving)
    question = (body.question or "").strip()
    if question:
        summary = f"就「{question}」而言，宜先辨本卦与动变之消息，再按解卦通则取舍。"
        advice = [
            "对照卦辞与动爻，把所问落到一件可做之事上。",
            "占得仅供参考，关键仍在于审时度势、守中而行。",
        ]
        clip = question if len(question) <= 16 else question[:16] + "…"
        ask_next = [
            f"我「{clip}」接下来会怎样？",
            "我眼下最该先做什么？",
        ]
    else:
        summary = "所问未明，宜先回观本卦与动变之消息，再按解卦通则取舍。"
        advice = [
            "可点下面的「我的事业会如何？」或「我的感情会如何？」，把方向补上再往下问。",
            "占得仅供参考，关键仍在于审时度势、守中而行。",
        ]
        ask_next = list(EMPTY_QUESTION_ASK_NEXT)
    if body.resultingNumber is None and moving:
        advice.insert(0, "动爻已显，宜细玩动爻辞义，不必急求之卦。")
    risks = ["勿只看吉辞而忽其戒惧，亦勿因一句凶辞即停步不前。"]
    return AIAnalysisContent(
        summary=summary,
        focus=focus,
        direction="宜对照本卦大象，守中而动，把气力用在当下可尽之处。",
        risks=risks,
        advice=advice,
        askNext=ask_next,
    ), AIUsage(promptTokens=0, completionTokens=0)


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
        raise AppError("模型服务暂时不可用，请稍后重试", code=5000, status_code=502) from exc

    if resp.status_code >= 400:
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502)

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
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502) from exc
    return parsed, usage


def _string_list(value) -> list[str]:
    if isinstance(value, str):
        text = value.strip()
        return [text] if text else []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return []


def _strip_risk_prefix(text: str) -> str:
    for prefix in ("须防：", "须防:", "須防：", "須防:"):
        if text.startswith(prefix):
            return text[len(prefix):].strip()
    return text


def _one_risk(items: list[str]) -> list[str]:
    parts = [_strip_risk_prefix(item) for item in items]
    parts = [part for part in parts if part]
    if not parts:
        return []
    return ["；".join(parts)]


def _analysis_from_parsed(parsed: dict) -> AIAnalysisContent:
    advice = _string_list(parsed.get("advice"))
    return AIAnalysisContent(
        summary=str(parsed.get("summary", "")).strip(),
        focus=str(parsed.get("focus", "")).strip(),
        direction=str(parsed.get("direction", "")).strip(),
        risks=_one_risk(_string_list(parsed.get("risks"))),
        advice=advice,
        askNext=_string_list(parsed.get("askNext")),
    )


def _previous_analysis_block(prev: AIAnalysisContent) -> str:
    parts = [
        "此前解读：",
        f"事情背景：{prev.summary}",
        f"当下：{prev.focus}",
    ]
    if prev.direction:
        parts.append(f"方向：{prev.direction}")
    if prev.risks:
        parts.append("须防：" + "；".join(prev.risks))
    if prev.advice:
        parts.append("建议：" + "；".join(prev.advice))
    if prev.askNext:
        parts.append("可再问：" + "；".join(prev.askNext))
    return "\n".join(parts)


def _analyze_openai(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    system_prompt = (
        "你是「易玩家」的易经解读助手。依据提供的卦象与经文（《易经证释》所引）做占问解读。"
        "解读须按：卦辞→事情背景；大象辞→占者宜努力的方向；动爻之爻辞与小象→当下情形。"
        "本卦卦辞的黄庭书院讲解是事情大背景，须先消化再写事情背景；"
        "主看卦辞时另附彖辞讲解，只用来理解卦辞格局，不要与卦辞讲解重复成两套背景。"
        "大象与焦点爻辞讲解帮助理解辞义。"
        "结论从卦象、卦辞、彖辞、大象辞、爻辞、小象辞中归纳，紧扣「解卦焦点」。"
        "不要整段照抄讲解原文。若附有与本次焦点爻位相关的讲习案例，可参照其取象、应事与验证，"
        "但必须针对本次所问与动爻，不可把案例原事或结论直接套到用户身上。"
        "正文不要重复卡片标题。只输出 JSON，不要 markdown，格式："
        '{"summary":"事情背景2-4句，据卦辞","focus":"当下2-4句，据动爻爻辞/小象；无动爻则据卦辞与大象","direction":"方向2-3句，据大象辞","risks":["须防一条"],"advice":["可做1","可做2"],"askNext":["我接下来会怎样？","我最该先做什么？"]}'
        "askNext 必须是用户会亲口问出的短句（用「我」），不要写成对用户的提问或建议。"
        "须防只写一条。"
        "若所问未填，advice 须请用户点选事业或感情方向；askNext 仍可随便写，服务端会改成「我的事业会如何？」「我的感情会如何？」。"
    )
    parsed, usage = _complete_json(system_prompt, _build_prompt(body))
    analysis = _apply_empty_question_ask_next(body.question, _analysis_from_parsed(parsed))
    if (
        not analysis.summary
        or not analysis.focus
        or not analysis.direction
        or not analysis.advice
        or not analysis.risks
        or not analysis.askNext
    ):
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502)
    return analysis, usage


def _followup_prompt(body: AIFollowupBody) -> str:
    parts = [
        _reading_sketch(body),
        _previous_analysis_block(body.previousAnalysis),
    ]
    if body.conversation:
        parts.append("此前追问：")
        for index, turn in enumerate(body.conversation[-10:], start=1):
            line = f"{index}. 用户：{turn.user}\n   助手：{turn.assistant}"
            if turn.advice:
                line += "\n   建议：" + "；".join(turn.advice)
            parts.append(line)
    parts.append(f"用户最新追问或补充：{body.message.strip()}")
    if body.message.strip() in EMPTY_QUESTION_ASK_NEXT:
        parts.append("用户点选了所问方向（事业或感情），请按该方向收窄此前解读，并可请其再用一句话落到具体事情上。")
    parts.append(
        "请针对这条追问/补充作答，以解卦焦点和此前解读为准，可修正或细化，不要另起一卦之占，不要重贴经文。"
        "答复后必须给出针对这一轮的建议，以及用户可以继续追问的短句。"
    )
    return "\n\n".join(parts)


def _followup_mock(body: AIFollowupBody) -> Tuple[str, list[str], list[str], AIUsage]:
    text = body.message.strip()
    clipped = text if len(text) <= 40 else text[:40] + "…"
    reply = f"已记下你的补充「{clipped}」。请仍对照本卦动爻与此前解读，把新背景收进判断里。"
    advice = [
        "把这条补充收进判断，仍以解卦焦点为主，不要另起一套说法。",
        "占得仅供参考，先做一件眼下能做的。",
    ]
    ask_next = [
        "情况再变的话，我该守还是该转？",
        "我眼下最该先做什么？",
    ]
    return reply, advice, ask_next, AIUsage(promptTokens=0, completionTokens=0)


def _followup_openai(body: AIFollowupBody) -> Tuple[str, list[str], list[str], AIUsage]:
    system_prompt = (
        "你是「易玩家」的易经解读助手。用户已得到初次解读，现在追问或补充背景。"
        "结合解卦焦点、焦点经文与此前解读作答；有新背景时据此调整判断。"
        "不要另起一卦之占。用户可能点选「可以接着问」里的短问（含「我的事业会如何？」「我的感情会如何？」以补方向），或自己补充背景；针对这一条作答即可。"
        "askNext 必须是用户会亲口问出的短句（用「我」）。"
        "不要重贴经文，不要整段照抄讲解。"
        "答复之后必须另给建议（可做之事）和可继续追问的短句。"
        "正文不要重复卡片标题。只输出 JSON，不要 markdown，格式："
        '{"reply":"针对追问的答复，可分2-6句","advice":["可做1","可做2"],"askNext":["我会怎样？","我最该先做什么？"]}'
    )
    parsed, usage = _complete_json(system_prompt, _followup_prompt(body))
    reply = str(parsed.get("reply", "")).strip()
    advice = _string_list(parsed.get("advice"))
    ask_next = _string_list(parsed.get("askNext"))
    if not reply or not advice or not ask_next:
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502)
    return reply, advice, ask_next, usage


def analyze_reading(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    mode = (settings.ai_mode or "mock").lower()
    if mode == "openai":
        return _analyze_openai(body)
    return _analyze_mock(body)


def followup_reading(body: AIFollowupBody) -> Tuple[str, list[str], list[str], AIUsage]:
    mode = (settings.ai_mode or "mock").lower()
    if mode == "openai":
        return _followup_openai(body)
    return _followup_mock(body)
