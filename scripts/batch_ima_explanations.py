#!/usr/bin/env python3
"""Batch-ask 黄庭书院 via ima-kb-qa ask-batch.mjs (one tab, many questions).

爻辞与小象合并提问；回答写入双方 id。
Resumable: skips ids already answered well in answers.json.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from ima_units import build_ask_jobs, is_bad, make_entry  # noqa: E402

DIR = ROOT / "data" / "ima-explanations"
CATALOG = DIR / "catalog.json"
ANSWERS = DIR / "answers.json"
PENDING = DIR / "pending.jsonl"
STREAM = DIR / "stream.jsonl"
ASK_BATCH = Path.home() / ".agents/skills/ima-kb-qa/scripts/ask-batch.mjs"
LOG = DIR / "batch.log"

PROMPT = "{text}"


def log(msg: str) -> None:
    line = f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} {msg}"
    print(line, flush=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def save_answers(data: dict) -> None:
    tmp = ANSWERS.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(ANSWERS)


def apply_row(store: dict, catalog_map: dict, job_map: dict, row: dict) -> None:
    now = datetime.now(timezone.utc).isoformat()
    job_id = row["id"]
    job = job_map.get(job_id, {"targets": [job_id], "text": row.get("text")})
    targets = job.get("targets") or [job_id]
    ask_text = row.get("text") or job.get("text") or ""
    answer = row.get("answer")
    error = row.get("error")

    probe = {"answer": answer, "error": error}
    ok = bool(answer) and not is_bad(probe)

    for uid in targets:
        meta = catalog_map.get(uid, {})
        if ok:
            store["answers"][uid] = make_entry(uid, meta, ask_text, answer, None, now)
        elif error:
            store["answers"][uid] = make_entry(uid, meta, ask_text, None, error, now)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--only", type=str, default="")
    ap.add_argument("--reopen-every", type=int, default=15)
    ap.add_argument("--pause-ms", type=int, default=0)
    args = ap.parse_args()

    if not ASK_BATCH.exists():
        log(f"missing {ASK_BATCH}")
        return 1

    DIR.mkdir(parents=True, exist_ok=True)
    runtime_path = DIR / "runtime.json"
    runtime = json.loads(runtime_path.read_text(encoding="utf-8")) if runtime_path.exists() else {}
    pause_ms = args.pause_ms or int(runtime.get("pause_ms", 60000))
    reopen_every = args.reopen_every if args.reopen_every else int(runtime.get("reopen_every", 8))

    catalog = load_json(CATALOG)
    catalog_map = {u["id"]: u for u in catalog["units"]}
    store = load_json(ANSWERS) if ANSWERS.exists() else {
        "version": 1,
        "kb": "黄庭书院",
        "kbId": "7299369715913684",
        "promptTemplate": PROMPT,
        "answers": {},
    }
    store.setdefault("answers", {})
    store["promptTemplate"] = PROMPT
    store["askStrategy"] = "named hex ask; guaci/tuanci/daxiang alone; yaoci+xiaoxiang paired"

    units = catalog["units"]
    if args.only:
        wanted = {x.strip() for x in args.only.split(",") if x.strip()}
        units = [
            u for u in units
            if u["id"] in wanted
            or f"{u['number']:02d}-yao-{u['index']}" in wanted
        ]

    jobs = build_ask_jobs(units, store["answers"])
    if args.limit > 0:
        jobs = jobs[: args.limit]

    good = sum(1 for a in store["answers"].values() if a.get("answer") and not is_bad(a))
    log(f"total_units={len(catalog['units'])} good={good} ask_jobs={len(jobs)} pause_ms={pause_ms} (yao+xiao paired)")
    if not jobs:
        log("nothing to do")
        return 0

    job_map = {j["id"]: j for j in jobs}
    with PENDING.open("w", encoding="utf-8") as f:
        for j in jobs:
            f.write(json.dumps({"id": j["id"], "text": j["text"], "targets": j["targets"]}, ensure_ascii=False) + "\n")

    if STREAM.exists():
        STREAM.unlink()

    env = {
        **os.environ,
        "IMA_KB": "黄庭书院",
        "IMA_HEADLESS_OUTPUT": "0",
        "IMA_WAIT_MS": "180000",
        "IMA_PAUSE_MS": str(pause_ms),
        "IMA_REOPEN_EVERY": str(reopen_every),
        "IMA_COOLDOWN_MS": str(int(runtime.get("cooldown_extra_ms", 180000))),
        "PYTHONUNBUFFERED": "1",
    }
    log(f"start ask-batch.mjs jobs={len(jobs)} reopen_every={reopen_every} pause_ms={pause_ms}")
    proc = subprocess.Popen(
        ["node", str(ASK_BATCH), "--in", str(PENDING), "--out", str(STREAM)],
        cwd=str(ROOT),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    assert proc.stdout is not None
    last_merge = 0
    try:
        for line in proc.stdout:
            line = line.rstrip()
            if line:
                log(line)
            if STREAM.exists() and STREAM.stat().st_size > last_merge:
                store = load_json(ANSWERS) if ANSWERS.exists() else store
                with STREAM.open(encoding="utf-8") as sf:
                    for sline in sf:
                        sline = sline.strip()
                        if not sline:
                            continue
                        apply_row(store, catalog_map, job_map, json.loads(sline))
                save_answers(store)
                last_merge = STREAM.stat().st_size
    finally:
        rc = proc.wait()
        if STREAM.exists():
            store = load_json(ANSWERS) if ANSWERS.exists() else store
            with STREAM.open(encoding="utf-8") as sf:
                for sline in sf:
                    sline = sline.strip()
                    if not sline:
                        continue
                    apply_row(store, catalog_map, job_map, json.loads(sline))
            save_answers(store)
        good = sum(1 for a in store["answers"].values() if a.get("answer") and not is_bad(a))
        remaining = len(build_ask_jobs(catalog["units"], store["answers"]))
        log(f"finished rc={rc} good={good} remaining_jobs={remaining}")
        return 0 if rc == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
