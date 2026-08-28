"""AI 用量事件：只记次数 / token / 成败 / 耗时，不写所问或解读正文。"""

from __future__ import annotations

import time
from datetime import datetime, timezone
from typing import Any, Callable, Optional, Tuple

from sqlalchemy.orm import Session

from app.config import settings
from app.errors import AppError
from app.models import AIUsageEvent
from app.schemas import AIUsage
from app.services.ai_rate_limit import acquire_ai_call

_FORBIDDEN_EVENT_FIELDS = frozenset(
    {
        "question",
        "analysis",
        "reply",
        "advice",
        "askNext",
        "previousAnalysis",
        "conversation",
        "message",
        "primaryNumber",
        "resultingNumber",
        "movingPositions",
        "lines",
    }
)


def current_ai_model() -> str:
    if (settings.ai_mode or "mock").lower() != "openai":
        return "mock"
    return settings.openai_model or "openai"


def record_ai_usage_event(
    db: Session,
    *,
    user_id: str,
    kind: str,
    method: Optional[str],
    ok: bool,
    error_code: Optional[int],
    latency_ms: int,
    prompt_tokens: int,
    completion_tokens: int,
) -> None:
    event = AIUsageEvent(
        created_at=datetime.now(timezone.utc),
        user_id=user_id,
        kind=kind,
        ok=ok,
        error_code=error_code,
        latency_ms=max(0, int(latency_ms)),
        prompt_tokens=max(0, int(prompt_tokens)),
        completion_tokens=max(0, int(completion_tokens)),
        model=current_ai_model(),
        method=(method or None),
    )
    leaked = _FORBIDDEN_EVENT_FIELDS.intersection(event.__dict__.keys())
    if leaked:
        raise RuntimeError(f"ai_usage_events must not store {sorted(leaked)}")
    try:
        db.add(event)
        db.commit()
    except Exception as exc:
        db.rollback()
        print(f"[ai_usage] record failed: {exc}")


def run_logged_ai(
    db: Session,
    *,
    user_id: str,
    kind: str,
    method: str,
    fn: Callable[[], Tuple[Any, AIUsage]],
) -> Tuple[Any, AIUsage]:
    started = time.perf_counter()
    usage = AIUsage(promptTokens=0, completionTokens=0)
    ok = False
    error_code: Optional[int] = None
    try:
        with acquire_ai_call(user_id):
            out, usage = fn()
        ok = True
        return out, usage
    except AppError as exc:
        error_code = exc.code
        raise
    except Exception:
        error_code = 5000
        raise
    finally:
        record_ai_usage_event(
            db,
            user_id=user_id,
            kind=kind,
            method=method,
            ok=ok,
            error_code=error_code,
            latency_ms=int((time.perf_counter() - started) * 1000),
            prompt_tokens=usage.promptTokens,
            completion_tokens=usage.completionTokens,
        )
