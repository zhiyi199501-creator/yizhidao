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


def _yao_name_en(position: int) -> str:
    names = {1: "first", 2: "second", 3: "third", 4: "fourth", 5: "fifth", 6: "top"}
    return names.get(position, str(position))


def _focus_text(moving: List[int], english: bool = False) -> str:
    count = len(moving)
    if english:
        if count == 0:
            return "No line moved. Attend to the Judgment of the primary hexagram."
        if count == 1:
            return f"One line moved. Attend to the {_yao_name_en(moving[0])} line of the primary hexagram."
        if count == 2:
            lead = moving[-1]
            return (
                "Two lines moved. Attend to both moving lines of the primary hexagram; "
                f"the {_yao_name_en(lead)} line leads."
            )
        if count == 3:
            return "Three lines moved. Attend to the Judgments of both hexagrams; the primary leads."
        if count == 4:
            statics = [p for p in range(1, 7) if p not in moving]
            lead = statics[0] if statics else 1
            return (
                "Four lines moved. Attend to the two still lines of the relating hexagram; "
                f"the {_yao_name_en(lead)} line leads."
            )
        if count == 5:
            statics = [p for p in range(1, 7) if p not in moving]
            lead = statics[0] if statics else 1
            return f"Five lines moved. Attend to the still {_yao_name_en(lead)} line of the relating hexagram."
        return "All six lines moved. Attend to the Judgment of the relating hexagram."
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
    question = (body.question or "").strip()
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
    question = (body.question or "").strip()

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
    if _ui_english(body):
        parts.insert(0, EN_OUTPUT_LOCK)
        parts.append(
            "Write the cards in English. Quote any scripture in the original Chinese; do not translate the 经文. "
            "Judgment (卦辞) → background; moving-line texts, 小象, and the Image (大象) → the detailed reading. "
            "Huangting notes help you read the words; cases are only for taking images, never copy their stories. "
            + EN_ANALOGY
            + EN_VOICE
            + " Fill summary, focus, and short follow-ups in the user's voice (I / my). Do not output advice or direction. Background and detailed reading about 6–12 sentences each."
        )
    else:
        parts.append(
            "解读框架：卦辞看事情背景（先参本卦卦辞讲解，彖辞助理解格局，不要另起一套背景）；"
            "动爻之爻辞、小象与大象辞一并写入详细解读（无动爻时，详细解读参本卦卦辞与大象）。"
            "黄庭讲解用于理解辞义；讲习案例若附上则只作取象参照。结论必须针对本次所问。"
            + ZH_ANALOGY
            + ZH_VOICE
            + "请分别写清事情背景与详细解读，各大约 6 到 12 句，并给出用户可直接点选发出的短追问（用「我」的口吻）。不要写建议，不要写方向字段。"
        )
    return "\n\n".join(parts)


def _ui_english(body: AIAnalysisBody) -> bool:
    return (body.uiLanguage or "zh").lower().startswith("en")


ZH_VOICE = (
    "用大白话写给普通人看，像跟熟人把这件事说清楚。"
    "先把经文意思用白话讲出来，再落到本次所问上；引经文只引短句原文，随后必须用白话解释。"
    "不要文言腔，不要堆「宜」「须」「勿」，不要空洞套话。"
    "写深、写透：宁可长一点也不要点到为止。"
    "该提醒的用白话写进详细解读，不要另开建议或戒惧。"
    "不要输出 direction／方向，也不要输出 advice／建议。"
)

EN_VOICE = (
    "Write in plain English, as if explaining to a friend. "
    "First say what the 经文 means in everyday words, then apply it to this question. "
    "Quote scripture only as short Chinese phrases, then explain in English. "
    "No archaisms, no vague filler. Go deep; prefer a fuller answer over a sketch. "
    "Fold any caution into the detailed reading in plain words. "
    "Do not output a direction field or advice."
)

# 从讲习口播 294 篇提炼的断卦规矩；不灌讲座原文、不写讲师名。不灌纳甲、互卦。
ZH_ANALOGY = (
    "断卦用类比，把经文的象移到本次所问上，不是让原文那件事再演一遍。"
    "类比按所问放大或缩小；同一辞，处境不同也要活断。"
    "先听清真正在问什么：口问含糊时，往心里要的方向断，不要被字面带跑。"
    "辞里若另有比所问更急的主轴，要点出来，但仍回答这一问。"
    "先对卦名与卦象：断卦不能只读辞，要回到象。气氛要跟所问同气；对不上就明说别扭在哪。"
    "卦名是天气，不是判词。吉卦不必是好运，凶卦也不必是定局；不要看到困、剥就先吓住，也不要看到泰、升就先恭喜。"
    "读辞分四层：卦是布幕（天气与来路，可点这卦从哪一卦来，但不另占那一卦）；"
    "大象辞看当事人该怎么自处（与所问无关则可略）；焦点爻辞是正在演的那一幕；小象辞补足爻辞，防漏读。"
    "吉凶从象来；辞与所问无关的可略过，勿硬套。紧扣解卦焦点，不要另主看别的爻。"
    "占到哪一爻就站在哪一爻看：那一爻是问事人这边的位，不要仍把那一爻当对面的人。"
    "爻位是阶段：初为始，二三为人世，三四为内外交接（气常会顿一下），上为终。"
    "下卦多是内、前半段，上卦多是外、后半段。初二为地、三四为人、五上为天。"
    "上下卦可类比两造、主宾、内外。「往」常是向外求，「来」常是向内收或回头。"
    "当位则各就其位、事较顺；不当位则别扭、易有疏失。"
    "相应是一阴一阳能通气（初与四、二与五、三与上）；不应则隔、远、合不上。只作旁证，勿另立主看。"
    "吉凶按所问的大小缩放：问小事不要按生死放大；问大事也不要把「凶」说轻。不要只抓一句吉词或凶词就下结论。"
    "经文无虚字：每个字都从象里讲出来再落到所问。不要死盯打仗、野外、父母、男女；阴爻阳爻都可以类比人或事。"
    "「征」常是硬往前，「居贞」常是守正道，不是困在家里。「勿用」是眼下不宜用，不是永远不能用。"
    "「无咎」是原来有过、出力可补；「无悔」「悔亡」是原来有悔、转一转才无；「有孚」常是一点点信、一点点福，不是大吉。"
    "「贞」是正而固；「厉」是危而须敬；「吝」是小疵。元亨利贞具足则事可成循环；亨小则通但不阔。"
    "命不是钉死的，人还能转。就这一占断完，不要暗示再起一卦。"
    "之卦只在与本卦同气、能相加时拿来加强，不要另起一占。"
    "讲习案例只借取象与应事的路数，禁止把案例里的人、病、器物套到用户身上。断不准的就明说，不要装作看见了具体人名、网站、病名。"
)

EN_ANALOGY = (
    "Read by analogy: move the image of the 经文 onto this question; do not restage the original story. "
    "Scale the analogy up or down to the matter; the same line in a different situation must be read afresh. "
    "Hear what is really being asked; if the wording is vague, read the intent, not the surface. "
    "If the text shows a more urgent axis than the wording, name it, but still answer this question. "
    "Do not read words alone: go back to the image. Match the hexagram's weather to the matter; if it does not fit, say so. "
    "The name is weather, not a verdict. Do not panic at 困 or 剥, and do not congratulate at 泰 or 升. "
    "Four layers: the hexagram is the backdrop (weather and how this affair arrived; you may note which hexagram it follows, but do not cast that one); "
    "the Image (大象) = how to conduct oneself (skip if off-topic); the focus line is the scene now playing; the small image (小象) fills gaps. "
    "吉凶 comes from the image; skip text that will not analogize. Stay with the reading focus. "
    "Stand in the line that moved: that line is the asker's place, not automatically the other party. "
    "Line place is stage: first = beginning, second/third = human affairs, third/fourth = inner–outer threshold (the qi often pauses), top = ending. "
    "Lower trigram ≈ inner / first half; upper ≈ outer / latter half. First two = earth, middle two = human, top two = heaven. "
    "The two trigrams may analogize two parties, host and guest, inner and outer. 往 often means going out to seek; 来 means drawing inward or turning back. "
    "当位 (a line in its own place) is smoother; 不当位 is awkward and prone to slips. "
    "相应 is one yin and one yang that can breathe together (1–4, 2–5, 3–6); if they do not, there is distance. Use this only as a side-check. "
    "Scale 吉凶 to the size of the question. Do not judge from a single lucky or unlucky phrase. "
    "No idle words. Do not take 行师, 野外, 父母, or gender literally. "
    "征 often means pushing ahead; 居贞 means holding the right course, not staying home. 勿用 means not to be used yet, not never. "
    "无咎 = fault that effort can mend; 无悔 / 悔亡 = regret until one turns; 有孚 is a little trust or blessing, not a jackpot. "
    "贞 = right and firm; 厉 = danger that wants care; 吝 = a small stain. Full 元亨利贞 is a workable cycle; 亨小 is open but not wide. "
    "Fate is not fixed. Finish this one cast; do not hint at recasting. "
    "Use the relating hexagram only when it reinforces the same weather. "
    "Cases are only for taking images; never paste their people, illnesses, or props onto the user. "
    "If you cannot see it, say so; do not invent names, websites, or diagnoses."
)

EN_OUTPUT_LOCK = (
    "OUTPUT LANGUAGE: English. "
    "Every JSON string you write (summary, focus, askNext, reply) must be English. "
    "The materials below are Chinese. Quote 经文 in Chinese; do not translate it. "
    "Do not write the cards or reply in Chinese. Do not output advice."
)


def _analyze_mock(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    moving = sorted(set(p for p in body.movingPositions if 1 <= p <= 6))
    english = _ui_english(body)
    focus = _focus_text(moving, english=english)
    question = (body.question or "").strip()
    clip = question if len(question) <= 16 else question[:16] + "…"
    if english:
        summary = (
            f"You asked about “{question}”. Start with the primary hexagram: what kind of stretch of road is this, "
            "and what is already in motion. The Judgment is the weather around the matter, not a yes-or-no stamp. "
            "Read that weather in plain words first, then set it against what you actually have to decide. "
            "Do not skip to a slogan. Name the situation as a living scene: who is waiting, what has already started, "
            "and where the pressure sits. Keep the relating hexagram for later; first see the ground under your feet."
        )
        ask_next = [
            f"What happens next with “{clip}”?",
            "What should I do first?",
        ]
        focus = (
            f"{focus} Say in ordinary words what the moving line and the Image are asking of you, "
            "and put your strength where it can still be spent."
        )
        if body.resultingNumber is None and moving:
            focus += " A line has moved: stay with that line for now; do not rush to the relating hexagram."
        return AIAnalysisContent(
            summary=summary,
            focus=focus,
            direction="",
            risks=[],
            advice=[],
            askNext=ask_next,
        ), AIUsage(promptTokens=0, completionTokens=0)
    summary = (
        f"你问的是「{question}」。先把本卦当成眼下这摊事的天气来看：局面已经走到哪一步、压力在谁身上、"
        "什么已经动起来了。卦辞不是盖章式的吉凶，是在说这件事处在什么样的时势里。"
        "先用白话把这层时势讲清楚，再拿它对照你真正要拍板的那一点。不要跳到一句口号就停。"
        "之卦可以稍后看；先看清脚下的地。"
    )
    ask_next = [
        f"我「{clip}」接下来会怎样？",
        "我眼下最该先做什么？",
    ]
    focus = (
        f"{focus}把爻辞、小象和大象用白话连起来讲：眼下卡在哪、气力该往哪使。"
        "守住中线，用在当下还能尽的地方，不要另起一套故事把力气打散。"
    )
    if body.resultingNumber is None and moving:
        focus += "动爻已经出来了，先把这一爻的意思看明白，不必急着追之卦。"
    return AIAnalysisContent(
        summary=summary,
        focus=focus,
        direction="",
        risks=[],
        advice=[],
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
    if settings.openai_max_tokens > 0:
        payload["max_tokens"] = int(settings.openai_max_tokens)
    headers = {
        "Authorization": f"Bearer {settings.openai_api_key}",
        "Content-Type": "application/json",
    }

    try:
        with httpx.Client(timeout=settings.openai_timeout_sec) as client:
            resp = client.post(url, headers=headers, json=payload)
    except httpx.HTTPError as exc:
        print(f"[ai] upstream transport error: {type(exc).__name__}: {exc}")
        raise AppError("模型服务暂时不可用，请稍后重试", code=5000, status_code=502) from exc

    if resp.status_code >= 400:
        print(f"[ai] upstream HTTP {resp.status_code}: {resp.text[:500]}")
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


def _analysis_from_parsed(parsed: dict) -> AIAnalysisContent:
    return AIAnalysisContent(
        summary=str(parsed.get("summary", "")).strip(),
        focus=str(parsed.get("focus", "")).strip(),
        direction="",
        risks=[],
        advice=[],
        askNext=_string_list(parsed.get("askNext")),
    )


def _previous_analysis_block(prev: AIAnalysisContent, english: bool = False) -> str:
    if english:
        parts = [
            "Earlier reading:",
            f"Background: {prev.summary}",
            f"Detailed reading: {prev.focus}",
        ]
        if prev.askNext:
            parts.append("Ask next: " + "; ".join(prev.askNext))
        return "\n".join(parts)
    parts = [
        "此前解读：",
        f"事情背景：{prev.summary}",
        f"详细解读：{prev.focus}",
    ]
    if prev.askNext:
        parts.append("可再问：" + "；".join(prev.askNext))
    return "\n".join(parts)


def _analyze_openai(body: AIAnalysisBody) -> Tuple[AIAnalysisContent, AIUsage]:
    if _ui_english(body):
        system_prompt = (
            f"{EN_OUTPUT_LOCK} "
            "You are the Yijing reading assistant for 易玩家. Use the hexagrams and the Chinese scripture "
            "as quoted in 《易经证释》. Write every card in English. Quote any 经文 in the original Chinese; "
            "do not translate the scripture, Wilhelm, or Legge. "
            "Judgment (卦辞) → background; moving-line texts, 小象, and the Image (大象) → the detailed reading (focus). "
            "Huangting notes help you read the words; do not copy them. Cases are only for taking images; "
            "never paste their stories onto the user. "
            + EN_ANALOGY
            + EN_VOICE
            + " Do not repeat card titles in the body. JSON only, no markdown: "
            '{"summary":"plain-English background from the Judgment, 6-12 sentences","focus":"detailed reading in plain English from the moving line, 小象, and Image; if no moving line, from Judgment and Image, 6-12 sentences","askNext":["What happens next?","What should I do first?"]}'
            "Do not output advice. askNext must be short questions the user would say (I / my), not advice to the user."
        )
    else:
        system_prompt = (
            "你是「易玩家」的易经解读助手。依据提供的卦象与经文（《易经证释》所引）做占问解读。"
            "解读须按：卦辞→事情背景；动爻爻辞、小象与大象辞→详细解读。"
            "本卦卦辞的黄庭书院讲解是事情大背景，须先消化再写事情背景；"
            "主看卦辞时另附彖辞讲解，只用来理解卦辞格局，不要与卦辞讲解重复成两套背景。"
            "大象与焦点爻辞讲解帮助理解辞义，写进详细解读，不要另开方向段。"
            "结论从卦象、卦辞、彖辞、大象辞、爻辞、小象辞中归纳，紧扣「解卦焦点」。"
            + ZH_ANALOGY
            + "不要整段照抄讲解原文。若附有与本次焦点爻位相关的讲习案例，可参照其取象、应事与验证，"
            "但必须针对本次所问与动爻，不可把案例原事或结论直接套到用户身上。"
            + ZH_VOICE
            + "正文不要重复卡片标题。只输出 JSON，不要 markdown，格式："
            '{"summary":"事情背景，大白话，据卦辞展开","focus":"详细解读，大白话，据动爻爻辞/小象与大象展开；无动爻则据卦辞与大象","askNext":["我接下来会怎样？","我最该先做什么？"]}'
            "不要输出建议。askNext 必须是用户会亲口问出的短句（用「我」），不要写成对用户的提问或建议。"
        )
    parsed, usage = _complete_json(system_prompt, _build_prompt(body))
    analysis = _analysis_from_parsed(parsed)
    if (
        not analysis.summary
        or not analysis.focus
        or not analysis.askNext
    ):
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502)
    return analysis, usage


def _followup_prompt(body: AIFollowupBody) -> str:
    english = _ui_english(body)
    parts = [
        _reading_sketch(body),
        _previous_analysis_block(body.previousAnalysis, english=english),
    ]
    if body.conversation:
        parts.append("Earlier follow-ups:" if english else "此前追问：")
        for index, turn in enumerate(body.conversation[-10:], start=1):
            if english:
                line = f"{index}. User: {turn.user}\n   Assistant: {turn.assistant}"
            else:
                line = f"{index}. 用户：{turn.user}\n   助手：{turn.assistant}"
            parts.append(line)
    if english:
        parts.insert(0, EN_OUTPUT_LOCK)
        parts.append(f"Latest follow-up: {body.message.strip()}")
        parts.append(
            "Answer this follow-up in English. Quote any scripture in Chinese; do not translate the 经文. "
            "Stay with the same cast. " + EN_VOICE
            + " Keep analogizing the focus text onto this turn; do not paste case stories. Hear the real question; scale 吉凶 to its size. "
            + " Write a deep reply of about 6–12 sentences. Give short follow-ups in the user's voice (I / my). "
            "Do not output advice."
        )
    else:
        parts.append(f"用户最新追问或补充：{body.message.strip()}")
        parts.append(
            "请针对这条追问/补充作答，以解卦焦点和此前解读为准，可修正或细化，不要另起一卦之占，不要重贴经文。"
            "仍用类比：把焦点经文的象移到这一问上，不要搬案例原事。听清这一问真正在问什么；吉凶按事情大小缩放。"
            + ZH_VOICE
            + "这一轮答复写深、写透，大约 6 到 12 句，并给出用户可以继续追问的短句。"
            "不要输出建议。"
        )
    return "\n\n".join(parts)


def _followup_mock(body: AIFollowupBody) -> Tuple[str, list[str], list[str], AIUsage]:
    text = body.message.strip()
    clipped = text if len(text) <= 40 else text[:40] + "…"
    if _ui_english(body):
        reply = (
            f"Noted: “{clipped}”. Keep it against the moving line and the earlier reading; "
            "do not start a new cast. Say in ordinary words what this new piece changes, "
            "and what it does not. Then name one thing you can still do from here."
        )
        ask_next = [
            "If things shift, should I hold or turn?",
            "What should I do first?",
        ]
        return reply, [], ask_next, AIUsage(promptTokens=0, completionTokens=0)
    reply = (
        f"记下了你的补充「{clipped}」。还是对着本卦动爻和前面那篇解读来看，"
        "用白话把这条新背景收进去：它改了什么、没改什么，再说眼下还能做哪一件。"
        "不要另起一卦的说法。"
    )
    ask_next = [
        "情况再变的话，我该守还是该转？",
        "我眼下最该先做什么？",
    ]
    return reply, [], ask_next, AIUsage(promptTokens=0, completionTokens=0)


def _followup_openai(body: AIFollowupBody) -> Tuple[str, list[str], list[str], AIUsage]:
    if _ui_english(body):
        system_prompt = (
            f"{EN_OUTPUT_LOCK} "
            "You are the Yijing reading assistant for 易玩家. The user already has a first reading and is following up. "
            "Stay with the same cast. Write reply and askNext in English; quote any scripture in Chinese. "
            "They may tap a short askNext or add background. Answer this turn only. "
            "Keep analogizing; do not paste case stories. "
            + EN_VOICE
            + " askNext must be questions the user would say (I / my). Do not output advice. JSON only: "
            '{"reply":"plain English, about 6-12 sentences. Quote 经文 in Chinese.","askNext":["What happens next?","What should I do first?"]}'
        )
    else:
        system_prompt = (
            "你是「易玩家」的易经解读助手。用户已得到初次解读，现在追问或补充背景。"
            "结合解卦焦点、焦点经文与此前解读作答；有新背景时据此调整判断。"
            "不要另起一卦之占。用户可能点选「可以接着问」里的短问，或自己补充背景；针对这一条作答即可。"
            "仍用类比，不要搬案例原事。"
            + ZH_VOICE
            + "askNext 必须是用户会亲口问出的短句（用「我」）。"
            "不要重贴经文，不要整段照抄讲解。"
            "不要输出建议。"
            "正文不要重复卡片标题。只输出 JSON，不要 markdown，格式："
            '{"reply":"针对追问的大白话答复，写深一些，大约6到12句","askNext":["我会怎样？","我最该先做什么？"]}'
        )
    parsed, usage = _complete_json(system_prompt, _followup_prompt(body))
    reply = str(parsed.get("reply", "")).strip()
    ask_next = _string_list(parsed.get("askNext"))
    if not reply or not ask_next:
        raise AppError("解读没有完成，请稍后重试", code=5000, status_code=502)
    return reply, [], ask_next, usage


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
