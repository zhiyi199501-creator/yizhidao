#!/usr/bin/env python3
"""从 data/ima-explanations/ 重建包内 ImaExplanations.json。会覆盖手改与后台保存的 answer；改过后不要跑。"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from ima_units import is_bad  # noqa: E402

ANSWERS = ROOT / "data/ima-explanations/answers.json"
OUT = ROOT / "ios/Yizhidao/Resources/ImaExplanations.json"


def yao_head(yaoci_text: str) -> str:
    head = (yaoci_text or "").split("，", 1)[0].strip()
    return head or "爻"


def build_entries(answers: dict[str, dict]) -> dict[str, dict]:
    entries: dict[str, dict] = {}
    for uid, e in answers.items():
        if e.get("skipped") or is_bad(e):
            continue
        field = e.get("field")
        n = int(e["number"])
        name = e.get("name") or ""
        label = e.get("fieldLabel") or field
        ans = (e.get("answer") or "").strip()

        if field in ("yaoci", "xiaoxiang"):
            idx = e["index"]
            key = f"{n:02d}-yao-{idx}"
            if key in entries:
                continue
            y = answers.get(f"{n:02d}-yaoci-{idx}", {})
            x = answers.get(f"{n:02d}-xiaoxiang-{idx}", {})
            ytext = (y.get("text") or "").strip()
            xtext = (x.get("text") or "").strip()
            head = yao_head(ytext)
            scripture = ytext
            if xtext:
                scripture = f"{ytext}  小象：{xtext}" if ytext else xtext
            entries[key] = {
                "title": f"{name}卦 · {head}爻辞与小象",
                "scripture": scripture,
                "answer": ans,
            }
        elif field in ("yong", "yongxiang"):
            key = f"{n:02d}-yong"
            if key in entries:
                continue
            y = answers.get(f"{n:02d}-yong", {})
            x = answers.get(f"{n:02d}-yongxiang", {})
            ytext = (y.get("text") or "").strip()
            xtext = (x.get("text") or "").strip()
            label = (y.get("fieldLabel") or "用九用六").strip()
            scripture = ytext
            if xtext:
                scripture = f"{ytext}  象曰：{xtext}" if ytext else xtext
            entries[key] = {
                "title": f"{name}卦 · {label}",
                "scripture": scripture,
                "answer": ans,
            }
        else:
            entries[uid] = {
                "title": f"{name}卦 · {label}",
                "scripture": (e.get("text") or "").strip(),
                "answer": ans,
            }
    return entries


def main() -> None:
    if not ANSWERS.exists():
        raise SystemExit(f"missing {ANSWERS}")
    store = json.loads(ANSWERS.read_text(encoding="utf-8"))
    entries = build_entries(store.get("answers", {}))
    payload = {
        "version": 1,
        "source": "黄庭书院 · IMA 知识库",
        "entries": entries,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    size_mb = OUT.stat().st_size / 1024 / 1024
    print(f"wrote {OUT} entries={len(entries)} size_mb={size_mb:.2f}")


if __name__ == "__main__":
    main()
