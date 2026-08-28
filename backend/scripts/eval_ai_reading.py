"""本机对真实模型抽检 AI 解读。不进 CI。输出写到 backend/.eval/（gitignore）。

  cd backend && .venv/bin/python scripts/eval_ai_reading.py
  .venv/bin/python scripts/eval_ai_reading.py --dry-run
  .venv/bin/python scripts/eval_ai_reading.py --only career
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.config import settings
from app.errors import AppError
from app.schemas import AIAnalysisBody, AIAnalysisContent, AIFollowupBody
from app.services.ai import (
    _build_prompt,
    analyze_reading,
    explanation_slots,
    followup_reading,
)
from app.services.case_store import cases_for_ai_prompt
from app.services.hexagram_store import get_hexagram
from tests.eval_fixtures import FOLLOWUP, SAMPLES

OUT_DIR = ROOT / ".eval"
TZ = timezone(timedelta(hours=8))


def _resulting(primary: int, moving: List[int]) -> Optional[int]:
    if not moving:
        return None
    hexagram = get_hexagram(primary)
    if not hexagram:
        raise SystemExit(f"missing hexagram {primary}")
    bits = [int(ch) for ch in str(hexagram["binary"])]
    lookup = {}
    from app.services.hexagram_store import _load_hexagrams

    for number, item in _load_hexagrams().items():
        lookup[str(item.get("binary"))] = number
    for pos in moving:
        bits[pos - 1] = 1 - bits[pos - 1]
    key = "".join(str(b) for b in bits)
    return lookup[key]


def _body(sample: dict) -> AIAnalysisBody:
    moving = list(sample["moving"])
    resulting = _resulting(sample["primary"], moving)
    return AIAnalysisBody(
        question=sample["question"],
        method=sample["method"],
        primaryNumber=sample["primary"],
        resultingNumber=resulting,
        movingPositions=moving,
    )


def _inspect(sample: dict) -> Dict[str, Any]:
    body = _body(sample)
    slots = explanation_slots(body.primaryNumber, body.resultingNumber, body.movingPositions)
    caption, cases = cases_for_ai_prompt(
        body.primaryNumber, body.resultingNumber, body.movingPositions
    )
    prompt = _build_prompt(body)
    return {
        "id": sample["id"],
        "title": sample["title"],
        "question": sample["question"],
        "primary": body.primaryNumber,
        "resulting": body.resultingNumber,
        "moving": body.movingPositions,
        "ima": [entry_id for _, entry_id in slots],
        "case_caption": caption,
        "case_files": [str(item.get("file") or "") for item in cases],
        "case_count": len(cases),
        "prompt_chars": len(prompt),
    }


def _print_inspect(row: Dict[str, Any]) -> None:
    names = []
    for number in (row["primary"], row["resulting"]):
        if not number:
            names.append("—")
            continue
        hexagram = get_hexagram(number)
        names.append(f"{number}{(hexagram or {}).get('name') or ''}")
    print(f"\n## {row['id']}  {row['title']}")
    print(f"所问：{row['question']}")
    print(f"本卦/之卦：{names[0]} → {names[1]}  动爻：{row['moving'] or '无'}")
    print(f"黄庭：{', '.join(row['ima'])}")
    print(f"案例：{row['case_count']} 则  {row['case_caption']}")
    if row["case_files"]:
        print("  " + ", ".join(row["case_files"]))
    print(f"prompt 约 {row['prompt_chars']} 字")


def _print_analysis(content: AIAnalysisContent, usage: Any) -> None:
    print(f"事情背景：{content.summary}")
    print(f"当下：{content.focus}")
    print(f"方向：{content.direction}")
    print("须防：" + "；".join(content.risks))
    print("建议：" + "；".join(content.advice))
    print("可再问：" + "；".join(content.askNext))
    print(f"usage：prompt={usage.promptTokens} completion={usage.completionTokens}")


def _can_call() -> bool:
    return (settings.ai_mode or "").lower() == "openai" and bool(
        (settings.openai_api_key or "").strip()
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="抽检 AI 解读（本机真实模型）")
    parser.add_argument("--dry-run", action="store_true", help="只打印槽位与案例，不调模型")
    parser.add_argument("--only", help="只跑指定 sample id")
    args = parser.parse_args()

    samples = list(SAMPLES)
    if args.only:
        samples = [item for item in samples if item["id"] == args.only]
        if not samples:
            print(f"unknown id: {args.only}", file=sys.stderr)
            return 2

    call = (not args.dry_run) and _can_call()
    if not args.dry_run and not call:
        print("未调用模型：需要 AI_MODE=openai 且已配置 OPENAI_API_KEY。改为 --dry-run 槽位。")
        return 2

    print(
        f"mode={settings.ai_mode} model={settings.openai_model} "
        f"host={settings.openai_base_url.split('/')[2] if settings.openai_base_url else ''} "
        f"{'DRY-RUN' if not call else 'LIVE'}"
    )

    report: Dict[str, Any] = {
        "at": datetime.now(TZ).strftime("%Y-%m-%d %H:%M %z"),
        "model": settings.openai_model,
        "live": call,
        "samples": [],
    }

    follow_source = None
    for sample in samples:
        inspect = _inspect(sample)
        _print_inspect(inspect)
        record: Dict[str, Any] = {"inspect": inspect}
        if call:
            try:
                analysis, usage = analyze_reading(_body(sample))
            except AppError as exc:
                print(f"失败：{exc.message} (code={exc.code})")
                record["error"] = exc.message
            else:
                _print_analysis(analysis, usage)
                record["analysis"] = analysis.model_dump()
                record["usage"] = {
                    "promptTokens": usage.promptTokens,
                    "completionTokens": usage.completionTokens,
                }
                if sample["id"] == FOLLOWUP["after_id"]:
                    follow_source = (sample, analysis)
        report["samples"].append(record)

    if call and follow_source is not None:
        sample, previous = follow_source
        body = _body(sample)
        follow = AIFollowupBody(
            question=body.question,
            method=body.method,
            primaryNumber=body.primaryNumber,
            resultingNumber=body.resultingNumber,
            movingPositions=body.movingPositions,
            previousAnalysis=previous,
            conversation=[],
            message=FOLLOWUP["message"],
        )
        print(f"\n## followup  after {sample['id']}")
        print(f"追问：{FOLLOWUP['message']}")
        try:
            reply, advice, ask_next, usage = followup_reading(follow)
        except AppError as exc:
            print(f"失败：{exc.message} (code={exc.code})")
            report["followup"] = {"error": exc.message}
        else:
            print(f"回复：{reply}")
            print("建议：" + "；".join(advice))
            print("可再问：" + "；".join(ask_next))
            print(f"usage：prompt={usage.promptTokens} completion={usage.completionTokens}")
            report["followup"] = {
                "message": FOLLOWUP["message"],
                "reply": reply,
                "advice": advice,
                "askNext": ask_next,
                "usage": {
                    "promptTokens": usage.promptTokens,
                    "completionTokens": usage.completionTokens,
                },
            }

    if call:
        OUT_DIR.mkdir(exist_ok=True)
        stamp = datetime.now(TZ).strftime("%Y%m%d-%H%M")
        path = OUT_DIR / f"eval-{stamp}.json"
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n已写入 {path.relative_to(ROOT)}（勿提交）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
