from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional, Sequence

from sqlalchemy import and_, case, func, or_, select
from sqlalchemy.orm import Session

from app.config import settings
from app.errors import AppError
from app.models import AIUsageEvent, User, UserFeedback
from app.services.auth import mask_email, mask_phone
from app.services.case_store import case_catalog_status
from app.services.hexagram_store import hexagram_catalog_status
from app.services.ima_store import ima_catalog_status

CST = timezone(timedelta(hours=8))
RANGE_KEYS = {"today", "7d", "30d"}
_METHOD_LABELS = {
    "digitalManual": "数字起卦·三数",
    "digitalTime": "数字起卦·时间",
    "coin": "六爻金钱卦",
}


def _now_cst() -> datetime:
    return datetime.now(CST)


def start_of_today_cst() -> datetime:
    now = _now_cst()
    return now.replace(hour=0, minute=0, second=0, microsecond=0)


def range_start(range_key: str) -> datetime:
    key = (range_key or "today").strip()
    if key not in RANGE_KEYS:
        raise AppError("时间范围无效", code=4001, status_code=400)
    today = start_of_today_cst()
    if key == "today":
        return today
    if key == "7d":
        return today - timedelta(days=6)
    return today - timedelta(days=29)


def as_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=CST).astimezone(timezone.utc)
    return dt.astimezone(timezone.utc)


def _to_cst(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc).astimezone(CST)
    return dt.astimezone(CST)


def iso(dt: Optional[datetime]) -> Optional[str]:
    local = _to_cst(dt)
    return local.isoformat() if local else None


def login_methods(user: User) -> List[str]:
    methods: List[str] = []
    if user.apple_sub:
        methods.append("apple")
    if user.google_sub:
        methods.append("google")
    if user.email:
        methods.append("email")
    if user.phone:
        methods.append("phone")
    return methods


def primary_login_method(user: User) -> str:
    methods = login_methods(user)
    return methods[0] if methods else "unknown"


def _cost_usd(prompt_tokens: int, completion_tokens: int) -> Dict[str, Any]:
    prompt_rate = float(settings.ai_usd_per_1m_prompt_tokens or 0)
    completion_rate = float(settings.ai_usd_per_1m_completion_tokens or 0)
    configured = prompt_rate > 0 or completion_rate > 0
    usd = 0.0
    if configured:
        usd = (prompt_tokens / 1_000_000.0) * prompt_rate + (
            completion_tokens / 1_000_000.0
        ) * completion_rate
    return {
        "configured": configured,
        "usd": round(usd, 6) if configured else None,
    }


def _percentile(values: Sequence[int], p: float) -> Optional[int]:
    if not values:
        return None
    ordered = sorted(int(v) for v in values)
    if len(ordered) == 1:
        return ordered[0]
    idx = (len(ordered) - 1) * (p / 100.0)
    lo = int(idx)
    hi = min(lo + 1, len(ordered) - 1)
    frac = idx - lo
    return int(round(ordered[lo] * (1 - frac) + ordered[hi] * frac))


def _event_aggregates(db: Session, since: datetime) -> Dict[str, Any]:
    since_utc = as_utc(since)
    row = db.execute(
        select(
            func.count(AIUsageEvent.id),
            func.coalesce(func.sum(case((AIUsageEvent.ok.is_(True), 1), else_=0)), 0),
            func.coalesce(func.sum(AIUsageEvent.prompt_tokens), 0),
            func.coalesce(func.sum(AIUsageEvent.completion_tokens), 0),
            func.coalesce(func.sum(case((AIUsageEvent.error_code == 4290, 1), else_=0)), 0),
            func.coalesce(
                func.sum(
                    case(
                        (
                            and_(
                                AIUsageEvent.ok.is_(False),
                                or_(
                                    AIUsageEvent.error_code.is_(None),
                                    AIUsageEvent.error_code != 4290,
                                ),
                            ),
                            1,
                        ),
                        else_=0,
                    )
                ),
                0,
            ),
        ).where(AIUsageEvent.created_at >= since_utc)
    ).one()
    total = int(row[0] or 0)
    ok_count = int(row[1] or 0)
    prompt = int(row[2] or 0)
    completion = int(row[3] or 0)
    rate_429 = int(row[4] or 0)
    other_fail = int(row[5] or 0)
    by_kind = {
        str(kind): int(count)
        for kind, count in db.execute(
            select(AIUsageEvent.kind, func.count(AIUsageEvent.id))
            .where(AIUsageEvent.created_at >= since_utc)
            .group_by(AIUsageEvent.kind)
        )
    }
    return {
        "calls": total,
        "ok": ok_count,
        "successRate": round(ok_count / total, 4) if total else None,
        "promptTokens": prompt,
        "completionTokens": completion,
        "tokens": prompt + completion,
        "rateLimited": rate_429,
        "failedOther": other_fail,
        "analyze": by_kind.get("analyze", 0),
        "followup": by_kind.get("followup", 0),
        "cost": _cost_usd(prompt, completion),
    }


def _count_users_since(db: Session, since: datetime) -> int:
    return int(
        db.scalar(select(func.count(User.id)).where(User.created_at >= as_utc(since))) or 0
    )


def _empty_bucket() -> Dict[str, int]:
    return {"calls": 0, "promptTokens": 0, "completionTokens": 0}


def _add_event(bucket: Dict[str, int], event: AIUsageEvent) -> None:
    bucket["calls"] += 1
    bucket["promptTokens"] += int(event.prompt_tokens or 0)
    bucket["completionTokens"] += int(event.completion_tokens or 0)


def _series_point(date: str, bucket: Dict[str, int]) -> Dict[str, Any]:
    prompt = bucket["promptTokens"]
    completion = bucket["completionTokens"]
    return {
        "date": date,
        "calls": bucket["calls"],
        "promptTokens": prompt,
        "completionTokens": completion,
        "tokens": prompt + completion,
    }


def _daily_call_series(db: Session, start: datetime, days: int) -> List[Dict[str, Any]]:
    since_utc = as_utc(start)
    events = db.scalars(select(AIUsageEvent).where(AIUsageEvent.created_at >= since_utc)).all()
    buckets: Dict[str, Dict[str, int]] = defaultdict(_empty_bucket)
    for event in events:
        local = _to_cst(event.created_at)
        if local:
            _add_event(buckets[local.date().isoformat()], event)
    series = []
    for offset in range(days):
        day = (start + timedelta(days=offset)).date().isoformat()
        series.append(_series_point(day, buckets.get(day, _empty_bucket())))
    return series


def _hourly_call_series(db: Session, start: datetime) -> List[Dict[str, Any]]:
    since_utc = as_utc(start)
    until_utc = as_utc(start + timedelta(days=1))
    events = db.scalars(
        select(AIUsageEvent).where(
            AIUsageEvent.created_at >= since_utc,
            AIUsageEvent.created_at < until_utc,
        )
    ).all()
    buckets: Dict[int, Dict[str, int]] = defaultdict(_empty_bucket)
    for event in events:
        local = _to_cst(event.created_at)
        if local:
            _add_event(buckets[local.hour], event)
    day = start.date().isoformat()
    return [
        _series_point(f"{day}T{hour:02d}:00", buckets.get(hour, _empty_bucket()))
        for hour in range(24)
    ]


def content_health() -> Dict[str, Any]:
    cases = case_catalog_status()
    hexagrams = hexagram_catalog_status()
    ima = ima_catalog_status()
    return {
        "aiMode": settings.ai_mode,
        "model": settings.openai_model if (settings.ai_mode or "").lower() == "openai" else "mock",
        "casesVersion": cases.get("version") or "",
        "casesCount": cases.get("count") or 0,
        "casesLoaded": bool(cases.get("loaded")),
        "hexagramsLoaded": bool(hexagrams.get("loaded")),
        "hexagramsCount": hexagrams.get("count") or 0,
        "imaLoaded": bool(ima.get("loaded")),
        "imaCount": ima.get("count") or 0,
    }


def overview(db: Session) -> Dict[str, Any]:
    today = start_of_today_cst()
    week = today - timedelta(days=6)
    total_users = int(db.scalar(select(func.count(User.id))) or 0)
    login_mix = {"apple": 0, "google": 0, "email": 0, "phone": 0, "unknown": 0}
    for user in db.scalars(select(User)).all():
        login_mix[primary_login_method(user)] = login_mix.get(primary_login_method(user), 0) + 1
    feedback_total = int(db.scalar(select(func.count(UserFeedback.id))) or 0)
    feedback_unread = int(
        db.scalar(select(func.count(UserFeedback.id)).where(UserFeedback.read_at.is_(None))) or 0
    )
    return {
        "ok": True,
        "users": {
            "total": total_users,
            "today": _count_users_since(db, today),
            "last7d": _count_users_since(db, week),
            "loginMix": login_mix,
        },
        "aiToday": _event_aggregates(db, today),
        "aiLast7d": _daily_call_series(db, week, 7),
        "feedback": {"total": feedback_total, "unread": feedback_unread},
        "health": content_health(),
    }


def _user_count_map(db: Session, since: Optional[datetime] = None) -> Dict[str, int]:
    stmt = select(AIUsageEvent.user_id, func.count(AIUsageEvent.id)).group_by(AIUsageEvent.user_id)
    if since is not None:
        stmt = stmt.where(AIUsageEvent.created_at >= as_utc(since))
    return {str(user_id): int(count) for user_id, count in db.execute(stmt)}


def serialize_user(user: User, today_calls: int = 0, total_calls: int = 0) -> Dict[str, Any]:
    from app.services.iap import iap_source, is_paid_iap

    source = iap_source(user)
    return {
        "id": user.id,
        "nickname": user.nickname,
        "email": mask_email(user.email) if user.email else None,
        "phone": mask_phone(user.phone) if user.phone else None,
        "loginMethods": login_methods(user),
        "createdAt": iso(user.created_at),
        "lastLoginAt": iso(user.last_login_at),
        "aiToday": today_calls,
        "aiTotal": total_calls,
        "iapUnlocked": bool(user.iap_unlocked),
        "aiUnlimited": bool(getattr(user, "ai_unlimited", False)),
        "iapSource": source,
        "iapCanRevoke": bool(user.iap_unlocked) and not is_paid_iap(user),
    }


def list_users(db: Session, q: str, page: int, page_size: int) -> Dict[str, Any]:
    page = max(1, page)
    page_size = min(100, max(1, page_size))
    stmt = select(User)
    needle = (q or "").strip()
    if needle:
        like = f"%{needle}%"
        stmt = stmt.where(
            or_(
                User.id.like(like),
                User.nickname.like(like),
                User.email.like(like),
                User.phone.like(like),
            )
        )
    total = int(db.scalar(select(func.count()).select_from(stmt.subquery())) or 0)
    rows = db.scalars(
        stmt.order_by(User.created_at.desc()).offset((page - 1) * page_size).limit(page_size)
    ).all()
    today_map = _user_count_map(db, start_of_today_cst())
    total_map = _user_count_map(db)
    return {
        "ok": True,
        "total": total,
        "page": page,
        "pageSize": page_size,
        "users": [
            serialize_user(user, today_map.get(user.id, 0), total_map.get(user.id, 0))
            for user in rows
        ],
    }


def user_detail(db: Session, user_id: str) -> Dict[str, Any]:
    user = db.scalar(select(User).where(User.id == user_id))
    if not user:
        raise AppError("用户不存在", code=4001, status_code=404)
    today_map = _user_count_map(db, start_of_today_cst())
    total_map = _user_count_map(db)
    start = start_of_today_cst() - timedelta(days=13)
    events = db.scalars(
        select(AIUsageEvent).where(
            AIUsageEvent.user_id == user_id,
            AIUsageEvent.created_at >= as_utc(start),
        )
    ).all()
    buckets: Dict[str, int] = defaultdict(int)
    for event in events:
        local = _to_cst(event.created_at)
        if local:
            buckets[local.date().isoformat()] += 1
    daily = []
    for offset in range(14):
        day = (start + timedelta(days=offset)).date().isoformat()
        daily.append({"date": day, "calls": buckets.get(day, 0)})
    return {
        "ok": True,
        "user": serialize_user(user, today_map.get(user.id, 0), total_map.get(user.id, 0)),
        "daily": daily,
    }


def ai_usage(db: Session, range_key: str) -> Dict[str, Any]:
    start = range_start(range_key)
    since_utc = as_utc(start)
    summary = _event_aggregates(db, start)
    latencies = [
        int(value)
        for value in db.scalars(
            select(AIUsageEvent.latency_ms).where(AIUsageEvent.created_at >= since_utc)
        ).all()
    ]
    errors = [
        {"code": int(code), "count": int(count)}
        for code, count in db.execute(
            select(AIUsageEvent.error_code, func.count(AIUsageEvent.id))
            .where(
                AIUsageEvent.created_at >= since_utc,
                AIUsageEvent.ok.is_(False),
                AIUsageEvent.error_code.is_not(None),
            )
            .group_by(AIUsageEvent.error_code)
            .order_by(func.count(AIUsageEvent.id).desc())
        )
    ]
    methods = [
        {
            "method": method or "unknown",
            "label": _METHOD_LABELS.get(method or "", method or "unknown"),
            "count": int(count),
        }
        for method, count in db.execute(
            select(AIUsageEvent.method, func.count(AIUsageEvent.id))
            .where(AIUsageEvent.created_at >= since_utc)
            .group_by(AIUsageEvent.method)
            .order_by(func.count(AIUsageEvent.id).desc())
        )
    ]
    top_users = []
    for user_id, count in db.execute(
        select(AIUsageEvent.user_id, func.count(AIUsageEvent.id))
        .where(AIUsageEvent.created_at >= since_utc)
        .group_by(AIUsageEvent.user_id)
        .order_by(func.count(AIUsageEvent.id).desc())
        .limit(10)
    ):
        top_users.append({"id": str(user_id), "calls": int(count)})
    today = start_of_today_cst()
    days = 1 if range_key == "today" else (7 if range_key == "7d" else 30)
    return {
        "ok": True,
        "range": range_key,
        "from": iso(start),
        "summary": {
            **summary,
            "latencyMsP50": _percentile(latencies, 50),
            "latencyMsP95": _percentile(latencies, 95),
        },
        "errors": errors,
        "methods": methods,
        "topUsers": top_users,
        "series": _hourly_call_series(db, today)
        if range_key == "today"
        else _daily_call_series(db, start, days),
    }


def list_ai_events(
    db: Session, range_key: str, page: int, page_size: int
) -> Dict[str, Any]:
    page = max(1, page)
    page_size = min(100, max(1, page_size))
    since_utc = as_utc(range_start(range_key))
    total = int(
        db.scalar(
            select(func.count(AIUsageEvent.id)).where(AIUsageEvent.created_at >= since_utc)
        )
        or 0
    )
    events = db.scalars(
        select(AIUsageEvent)
        .where(AIUsageEvent.created_at >= since_utc)
        .order_by(AIUsageEvent.created_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    ).all()
    nicknames = {}
    ids = [event.user_id for event in events]
    if ids:
        for user in db.scalars(select(User).where(User.id.in_(ids))).all():
            nicknames[user.id] = user.nickname
    return {
        "ok": True,
        "range": range_key,
        "total": total,
        "page": page,
        "pageSize": page_size,
        "events": [
            {
                "id": event.id,
                "createdAt": iso(event.created_at),
                "userId": event.user_id,
                "nickname": nicknames.get(event.user_id),
                "kind": event.kind,
                "ok": event.ok,
                "errorCode": event.error_code,
                "latencyMs": event.latency_ms,
                "promptTokens": event.prompt_tokens,
                "completionTokens": event.completion_tokens,
                "model": event.model,
                "method": event.method,
            }
            for event in events
        ],
    }


def system_status() -> Dict[str, Any]:
    cases = case_catalog_status()
    hexagrams = hexagram_catalog_status()
    ima = ima_catalog_status()
    return {
        "ok": True,
        "health": True,
        "config": {
            "appEnv": settings.app_env,
            "aiMode": settings.ai_mode,
            "model": settings.openai_model,
            "emailProvider": settings.email_provider,
            "smsProvider": settings.sms_provider,
            "aiRateIntervalSec": settings.ai_rate_interval_sec,
            "aiRateDailyLimit": settings.ai_rate_daily_limit,
            "costConfigured": bool(
                settings.ai_usd_per_1m_prompt_tokens or settings.ai_usd_per_1m_completion_tokens
            ),
        },
        "data": {
            "cases": cases,
            "hexagrams": hexagrams,
            "ima": ima,
        },
        "rateLimitNote": "日限流是进程内存，Docker 默认 2 个 worker 会各算各的；看板次数以事件表为准。",
    }
