#!/usr/bin/env python3
"""Monitor ima explanation batch; report remaining; adjust pause on rate-limits."""
from __future__ import annotations

import json
import os
import signal
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from ima_units import build_ask_jobs, is_bad  # noqa: E402

DIR = ROOT / "data" / "ima-explanations"
ANSWERS = DIR / "answers.json"
RUNTIME = DIR / "runtime.json"
STATUS = DIR / "status.json"
PID_FILE = DIR / "batch.pid"
STDOUT = DIR / "batch.stdout"
CATALOG = DIR / "catalog.json"


def load_runtime() -> dict:
    if RUNTIME.exists():
        return json.loads(RUNTIME.read_text(encoding="utf-8"))
    return {"pause_ms": 60000, "reopen_every": 8}


def save_runtime(rt: dict) -> None:
    RUNTIME.write_text(json.dumps(rt, ensure_ascii=False, indent=2), encoding="utf-8")


def count_state() -> dict:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    total = len(catalog["units"])
    store = json.loads(ANSWERS.read_text(encoding="utf-8")) if ANSWERS.exists() else {"answers": {}}
    answers = store.get("answers", {})
    good_ids = []
    bad_ids = []
    for uid, entry in answers.items():
        if entry.get("skipped"):
            continue
        if is_bad(entry):
            bad_ids.append(uid)
        elif entry.get("answer"):
            good_ids.append(uid)
    jobs = build_ask_jobs(catalog["units"], answers)
    return {
        "total": total,
        "good": len(good_ids),
        "bad_in_store": len(bad_ids),
        "pending": total - len(good_ids),
        "ask_jobs": len(jobs),
        "bad_ids_sample": bad_ids[:10],
    }


def batch_alive() -> bool:
    if not PID_FILE.exists():
        return False
    try:
        pid = int(PID_FILE.read_text().strip())
        os.kill(pid, 0)
        return True
    except Exception:
        return False


def recent_rate_limits(n: int = 20) -> int:
    if not STDOUT.exists():
        return 0
    lines = STDOUT.read_text(encoding="utf-8", errors="ignore").splitlines()[-80:]
    return sum(1 for l in lines if "RATE_LIMITED" in l or "提问太快" in l)


def clear_bad_in_store() -> int:
    if not ANSWERS.exists():
        return 0
    store = json.loads(ANSWERS.read_text(encoding="utf-8"))
    answers = store.get("answers", {})
    removed = 0
    for uid, entry in list(answers.items()):
        if entry.get("skipped"):
            continue
        if is_bad(entry):
            del answers[uid]
            removed += 1
    store["answers"] = answers
    ANSWERS.write_text(json.dumps(store, ensure_ascii=False, indent=2), encoding="utf-8")
    return removed


def start_batch(rt: dict) -> int:
    if PID_FILE.exists():
        try:
            os.kill(int(PID_FILE.read_text().strip()), signal.SIGTERM)
            time.sleep(2)
        except Exception:
            pass
    subprocess.run(["pkill", "-f", "batch_ima_explanations.py|ask-batch.mjs"], capture_output=True)
    time.sleep(1)
    clear_bad_in_store()

    DIR.mkdir(parents=True, exist_ok=True)
    stdout = open(STDOUT, "a", encoding="utf-8")
    env = {
        **os.environ,
        "PYTHONUNBUFFERED": "1",
        "IMA_PAUSE_MS": str(rt["pause_ms"]),
        "IMA_REOPEN_EVERY": str(rt.get("reopen_every", 8)),
        "IMA_WAIT_MS": "180000",
        "IMA_COOLDOWN_MS": str(int(rt.get("cooldown_extra_ms", 180000))),
        "IMA_KB": "黄庭书院",
    }
    proc = subprocess.Popen(
        [
            "python3", "-u", str(ROOT / "scripts/batch_ima_explanations.py"),
            "--reopen-every", str(rt.get("reopen_every", 8)),
            "--pause-ms", str(rt["pause_ms"]),
        ],
        cwd=str(ROOT),
        stdout=stdout,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        start_new_session=True,
        env=env,
    )
    PID_FILE.write_text(str(proc.pid))
    return proc.pid


def main() -> None:
    rt = load_runtime()
    state = count_state()
    alive = batch_alive()
    rl = recent_rate_limits()

    adjusted = False
    action = "none"
    if rt.get("paused"):
        action = "paused"
    elif state.get("ask_jobs", state["pending"]) <= 0:
        action = "complete"
    elif not alive:
        if rl >= 3:
            rt["pause_ms"] = min(int(rt.get("pause_ms", 60000) * 1.5), 300000)
            rt["last_adjust"] = datetime.now().isoformat(timespec="seconds")
            rt["notes"] = f"bumped pause to {rt['pause_ms']}ms due to rate limits"
            adjusted = True
            save_runtime(rt)
        pid = start_batch(rt)
        action = f"restarted pid={pid}"
    elif rl >= 5:
        rt["pause_ms"] = min(int(rt.get("pause_ms", 60000) * 1.5), 300000)
        rt["last_adjust"] = datetime.now().isoformat(timespec="seconds")
        rt["notes"] = f"live bump pause to {rt['pause_ms']}ms"
        adjusted = True
        save_runtime(rt)
        pid = start_batch(rt)
        action = f"slowed+restarted pid={pid}"

    report = {
        "at": datetime.now().isoformat(timespec="seconds"),
        "alive": alive if action == "none" else True,
        "pause_ms": rt.get("pause_ms"),
        "reopen_every": rt.get("reopen_every"),
        "recent_rate_limits": rl,
        "adjusted": adjusted,
        "action": action,
        **state,
    }
    STATUS.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
