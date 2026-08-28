from __future__ import annotations

from typing import Any, Dict, List, Optional

from app.config import settings
from app.errors import AppError
from app.schemas import AIAnalysisBody, AIFollowupBody
from app.services.ai import _build_prompt, analyze_reading, explanation_slots, followup_reading
from app.services.case_store import cases_for_ai_prompt
from app.services.hexagram_store import get_hexagram, _load_hexagrams
from tests.eval_fixtures import FOLLOWUP, SAMPLES


def _resulting(primary: int, moving: List[int]) -> Optional[int]:
    if not moving:
        return None
    hexagram = get_hexagram(primary)
    if not hexagram:
        return None
    bits = [int(ch) for ch in str(hexagram["binary"])]
    lookup = {str(item.get("binary")): number for number, item in _load_hexagrams().items()}
    for pos in moving:
        bits[pos - 1] = 1 - bits[pos - 1]
    return lookup.get("".join(str(bit) for bit in bits))


def _body(sample: dict) -> AIAnalysisBody:
    moving = list(sample["moving"])
    return AIAnalysisBody(
        question=sample["question"],
        method=sample["method"],
        primaryNumber=sample["primary"],
        resultingNumber=_resulting(sample["primary"], moving),
        movingPositions=moving,
    )


def list_samples() -> List[Dict[str, Any]]:
    return [
        {
            "id": sample["id"],
            "title": sample["title"],
            "question": sample["question"],
            "primary": sample["primary"],
            "moving": sample["moving"],
        }
        for sample in SAMPLES
    ]


def inspect_sample(sample: dict) -> Dict[str, Any]:
    body = _body(sample)
    slots = explanation_slots(body.primaryNumber, body.resultingNumber, body.movingPositions)
    ima = [entry_id for _, entry_id in slots]
    caption, cases = cases_for_ai_prompt(
        body.primaryNumber, body.resultingNumber, body.movingPositions
    )
    present_ok = all(entry_id in ima for entry_id in sample["expect_ima_ids"])
    absent_ok = not any(
        any(item == prefix or item.startswith(prefix) for item in ima)
        for prefix in sample["expect_ima_absent_prefixes"]
    )
    case_count = len(cases)
    if sample["expect_cases_from"] is None:
        cases_ok = case_count == 0
    else:
        cases_ok = bool(cases) and case_count <= sample["expect_case_count_max"] and all(
            item.get("number") == sample["expect_cases_from"] for item in cases
        )
    return {
        "id": sample["id"],
        "title": sample["title"],
        "question": sample["question"],
        "primary": body.primaryNumber,
        "resulting": body.resultingNumber,
        "moving": body.movingPositions,
        "ima": ima,
        "caseCaption": caption,
        "caseFiles": [str(item.get("file") or "") for item in cases],
        "caseCount": case_count,
        "promptChars": len(_build_prompt(body)),
        "checks": {
            "imaPresent": present_ok,
            "imaAbsent": absent_ok,
            "cases": cases_ok,
        },
        "pass": present_ok and absent_ok and cases_ok,
    }


def run_eval(ids: Optional[List[str]], live: bool) -> Dict[str, Any]:
    samples = list(SAMPLES)
    if ids:
        wanted = set(ids)
        samples = [sample for sample in samples if sample["id"] in wanted]
        if not samples:
            raise AppError("没有匹配的抽检样本", code=4001, status_code=400)
    results = []
    follow_source = None
    for sample in samples:
        inspect = inspect_sample(sample)
        record: Dict[str, Any] = {"inspect": inspect}
        if live:
            try:
                analysis, usage = analyze_reading(_body(sample))
            except AppError as exc:
                record["error"] = exc.message
            else:
                record["analysis"] = analysis.model_dump()
                record["usage"] = {
                    "promptTokens": usage.promptTokens,
                    "completionTokens": usage.completionTokens,
                }
                if sample["id"] == FOLLOWUP["after_id"]:
                    follow_source = (sample, analysis)
        results.append(record)

    followup = None
    if live and follow_source is not None:
        sample, previous = follow_source
        body = _body(sample)
        try:
            reply, advice, ask_next, usage = followup_reading(
                AIFollowupBody(
                    question=body.question,
                    method=body.method,
                    primaryNumber=body.primaryNumber,
                    resultingNumber=body.resultingNumber,
                    movingPositions=body.movingPositions,
                    previousAnalysis=previous,
                    conversation=[],
                    message=FOLLOWUP["message"],
                )
            )
        except AppError as exc:
            followup = {"error": exc.message, "message": FOLLOWUP["message"]}
        else:
            followup = {
                "message": FOLLOWUP["message"],
                "reply": reply,
                "advice": advice,
                "askNext": ask_next,
                "usage": {
                    "promptTokens": usage.promptTokens,
                    "completionTokens": usage.completionTokens,
                },
            }

    return {
        "ok": True,
        "live": live,
        "aiMode": settings.ai_mode,
        "pass": all(item["inspect"]["pass"] for item in results),
        "samples": results,
        "followup": followup,
        "note": "样本所问来自夹具，不是用户数据。出卡不写入用量事件。",
    }
