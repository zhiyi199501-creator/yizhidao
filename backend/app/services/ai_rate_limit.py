"""AI 解读/追问按登录用户限流（进程内）。自然日 UTC+8，解读与追问共用次数。"""

from __future__ import annotations

import threading
import time
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Callable, Dict, Iterator, Optional

from app.config import settings
from app.errors import AppError

_TZ = timezone(timedelta(hours=8))
MSG_TOO_FAST = "请稍后再试"
MSG_DAILY_DONE = "今天的解读次数用完了，明天再来"


class AIRateLimiter:
    def __init__(self, now: Optional[Callable[[], float]] = None) -> None:
        self._now = now or time.time
        self._lock = threading.Lock()
        self._users: Dict[str, Dict[str, object]] = {}

    def reset(self) -> None:
        with self._lock:
            self._users.clear()

    def _day(self, ts: float) -> str:
        return datetime.fromtimestamp(ts, tz=_TZ).date().isoformat()

    def acquire(self, user_id: str, daily_limit: Optional[int] = None) -> None:
        ts = self._now()
        day = self._day(ts)
        interval = float(settings.ai_rate_interval_sec)
        daily = int(settings.ai_rate_daily_limit if daily_limit is None else daily_limit)
        with self._lock:
            state = self._users.setdefault(
                user_id,
                {"day": day, "count": 0, "last_start": 0.0, "inflight": 0},
            )
            if state["day"] != day:
                state["day"] = day
                state["count"] = 0
            if int(state["inflight"]) >= 1:
                raise AppError(MSG_TOO_FAST, code=4290, status_code=429)
            if daily > 0 and int(state["count"]) >= daily:
                raise AppError(MSG_DAILY_DONE, code=4290, status_code=429)
            last = float(state["last_start"])
            if last and interval > 0 and ts - last < interval:
                raise AppError(MSG_TOO_FAST, code=4290, status_code=429)
            state["inflight"] = int(state["inflight"]) + 1
            state["last_start"] = ts
            state["count"] = int(state["count"]) + 1

    def snapshot(self, user_id: str, daily_limit: int) -> tuple[int, int]:
        """当日已用 / 剩余。不占额度。自然日 UTC+8；重启后进程内计数清零。"""
        ts = self._now()
        day = self._day(ts)
        daily = max(0, int(daily_limit))
        with self._lock:
            state = self._users.get(user_id)
            if not state or state["day"] != day:
                used = 0
            else:
                used = int(state["count"])
        remaining = max(0, daily - used) if daily > 0 else 0
        return used, remaining

    def release(self, user_id: str) -> None:
        with self._lock:
            state = self._users.get(user_id)
            if not state:
                return
            state["inflight"] = max(0, int(state["inflight"]) - 1)


limiter = AIRateLimiter()


@contextmanager
def acquire_ai_call(user_id: str, daily_limit: Optional[int] = None) -> Iterator[None]:
    limiter.acquire(user_id, daily_limit=daily_limit)
    try:
        yield
    finally:
        limiter.release(user_id)
