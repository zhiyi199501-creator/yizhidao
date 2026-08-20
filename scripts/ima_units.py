#!/usr/bin/env python3
"""Shared helpers for ima explanation batching."""
from __future__ import annotations

from typing import Any, Optional


def is_bad(entry: Optional[dict]) -> bool:
    if entry is None:
        return True
    if entry.get("skipped"):
        return False
    ans = entry.get("answer") or ""
    err = entry.get("error") or ""
    if err and not ans:
        return True
    if not ans:
        return True
    if "提问太快" in ans or "晚点再来问问" in ans:
        return True
    if len(ans) < 80:
        return True
    if len(ans) < 200 and ("您想" in ans or "您提到" in ans):
        return True
    return False


def needs_ask(entry: Optional[dict]) -> bool:
    if entry is None:
        return True
    if entry.get("skipped"):
        return False
    return is_bad(entry)


def _yao_title(yaoci_text: str) -> str:
    head = (yaoci_text or "").split("，", 1)[0].strip()
    return head or "爻"


def format_ask_text(unit: dict, xiao: Optional[dict] = None) -> str:
    """提问带卦名（单行）。"""
    name = unit.get("name") or ""
    label = unit.get("fieldLabel") or unit.get("field") or ""
    if unit.get("field") == "wenyan":
        return f"{name}卦文言：请依据知识库讲解《文言》全文。原文：{unit['text']}"
    if xiao is None:
        return f"{name}卦{label}：{unit['text']}"
    if unit.get("field") == "yong":
        return f"{name}卦{label}与象：{unit['text']} 象曰：{xiao['text']}"
    title = _yao_title(unit["text"])
    return f"{name}卦{title}爻辞与小象：{unit['text']} 小象：{xiao['text']}"


def build_ask_jobs(units: list[dict], answers: dict[str, dict]) -> list[dict[str, Any]]:
    """卦辞/彖/大象/文言单独问；爻辞+小象、用九/用六+象合并为一问。"""
    by_id = {u["id"]: u for u in units}
    jobs: list[dict[str, Any]] = []
    seen_pair: set[str] = set()

    for u in units:
        field = u["field"]
        if field in ("guaci", "tuanci", "daxiang", "wenyan"):
            if needs_ask(answers.get(u["id"])):
                jobs.append({
                    "id": u["id"],
                    "text": format_ask_text(u),
                    "targets": [u["id"]],
                })
            continue

        if field == "yong":
            n = u["number"]
            pair_key = f"{n:02d}-yong"
            if pair_key in seen_pair:
                continue
            seen_pair.add(pair_key)
            xid = f"{n:02d}-yongxiang"
            xu = by_id.get(xid)
            if xu is None:
                continue
            if not needs_ask(answers.get(u["id"])) and not needs_ask(answers.get(xid)):
                continue
            jobs.append({
                "id": pair_key,
                "text": format_ask_text(u, xu),
                "targets": [u["id"], xid],
                "number": n,
                "name": u["name"],
            })
            continue

        if field not in ("yaoci", "xiaoxiang"):
            continue

        n, idx = u["number"], u["index"]
        pair_key = f"{n:02d}-yao-{idx}"
        if pair_key in seen_pair:
            continue
        seen_pair.add(pair_key)

        yid = f"{n:02d}-yaoci-{idx}"
        xid = f"{n:02d}-xiaoxiang-{idx}"
        yu, xu = by_id.get(yid), by_id.get(xid)
        if yu is None or xu is None:
            continue
        if not needs_ask(answers.get(yid)) and not needs_ask(answers.get(xid)):
            continue

        jobs.append({
            "id": pair_key,
            "text": format_ask_text(yu, xu),
            "targets": [yid, xid],
            "number": n,
            "name": yu["name"],
            "index": idx,
        })

    return jobs


def make_entry(
    uid: str,
    meta: dict,
    text: str,
    answer: Optional[str],
    error: Optional[str],
    fetched_at: str,
) -> dict:
    return {
        "id": uid,
        "number": meta.get("number"),
        "name": meta.get("name"),
        "field": meta.get("field"),
        "fieldLabel": meta.get("fieldLabel"),
        "index": meta.get("index"),
        "text": meta.get("text") or text,
        "question": text,
        "answer": answer,
        "error": error,
        "fetchedAt": fetched_at,
        "pairedAsk": meta.get("field") in ("yaoci", "xiaoxiang", "yong", "yongxiang"),
    }


def build_catalog_units(hexagrams: list[dict]) -> list[dict]:
    units: list[dict] = []
    field_labels = {
        "guaci": "卦辞",
        "tuanci": "彖辞",
        "daxiang": "大象",
        "yaoci": "爻辞",
        "xiaoxiang": "小象",
        "wenyan": "文言",
    }
    for h in hexagrams:
        n = int(h["number"])
        name = h["name"]
        for field in ("guaci", "tuanci", "daxiang"):
            units.append({
                "id": f"{n:02d}-{field}",
                "number": n,
                "name": name,
                "field": field,
                "fieldLabel": field_labels[field],
                "index": None,
                "text": h[field],
            })
        for i, text in enumerate(h.get("yaoci") or []):
            units.append({
                "id": f"{n:02d}-yaoci-{i}",
                "number": n,
                "name": name,
                "field": "yaoci",
                "fieldLabel": field_labels["yaoci"],
                "index": i,
                "text": text,
            })
        for i, text in enumerate(h.get("xiaoxiang") or []):
            units.append({
                "id": f"{n:02d}-xiaoxiang-{i}",
                "number": n,
                "name": name,
                "field": "xiaoxiang",
                "fieldLabel": field_labels["xiaoxiang"],
                "index": i,
                "text": text,
            })
        yong = h.get("yong")
        if isinstance(yong, dict) and (yong.get("ci") or yong.get("xiang")):
            ci = (yong.get("ci") or "").strip()
            xiang = (yong.get("xiang") or "").strip()
            label = "用九" if "用九" in ci else ("用六" if "用六" in ci else "用九用六")
            units.append({
                "id": f"{n:02d}-yong",
                "number": n,
                "name": name,
                "field": "yong",
                "fieldLabel": label,
                "index": None,
                "text": ci,
            })
            units.append({
                "id": f"{n:02d}-yongxiang",
                "number": n,
                "name": name,
                "field": "yongxiang",
                "fieldLabel": f"{label}象",
                "index": None,
                "text": xiang,
            })
        wenyan = [p.strip() for p in (h.get("wenyan") or []) if (p or "").strip()]
        if wenyan:
            units.append({
                "id": f"{n:02d}-wenyan",
                "number": n,
                "name": name,
                "field": "wenyan",
                "fieldLabel": field_labels["wenyan"],
                "index": None,
                "text": "\n\n".join(wenyan),
            })
    return units
