import random
import re
import uuid
from datetime import datetime, timedelta, timezone

from jose import jwt
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.errors import AppError
from app.models import SMSCode, User

PHONE_PATTERN = re.compile(r"^1\d{10}$")


def validate_phone(phone: str) -> str:
    normalized = phone.strip()
    if not PHONE_PATTERN.fullmatch(normalized):
        raise AppError("手机号格式不正确", code=4001, status_code=400)
    return normalized


def mask_phone(phone: str) -> str:
    return f"{phone[:3]}****{phone[-4:]}"


def issue_access_token(user_id: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(days=settings.jwt_expire_days)
    payload = {"sub": user_id, "exp": expire}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def send_sms_code(db: Session, phone: str) -> int:
    phone = validate_phone(phone)
    now = datetime.now(timezone.utc)

    latest = db.scalar(
        select(SMSCode)
        .where(SMSCode.phone == phone)
        .order_by(SMSCode.created_at.desc())
        .limit(1)
    )
    if latest and latest.created_at:
        created = latest.created_at
        if created.tzinfo is None:
            created = created.replace(tzinfo=timezone.utc)
        elapsed = (now - created).total_seconds()
        if elapsed < settings.sms_cooldown_sec:
            raise AppError("请求过快，请稍后再试", code=4290, status_code=429)

    if settings.dev_sms_fixed_code:
        code = settings.dev_sms_fixed_code
    else:
        code = f"{random.randint(0, 999999):06d}"

    expires_at = now + timedelta(minutes=settings.sms_code_expire_min)
    db.add(SMSCode(phone=phone, code=code, expires_at=expires_at, used=False))
    db.commit()

    print(f"[sms] phone={mask_phone(phone)} code={code} expires={expires_at.isoformat()}")
    return settings.sms_cooldown_sec


def login_with_sms(db: Session, phone: str, code: str) -> tuple[User, str]:
    phone = validate_phone(phone)
    normalized_code = code.strip()
    if not normalized_code:
        raise AppError("验证码不能为空", code=4001, status_code=400)

    now = datetime.now(timezone.utc)
    record = db.scalar(
        select(SMSCode)
        .where(SMSCode.phone == phone, SMSCode.used.is_(False))
        .order_by(SMSCode.created_at.desc())
        .limit(1)
    )
    if not record:
        raise AppError("验证码错误或已过期", code=4002, status_code=400)

    expires_at = record.expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if record.code != normalized_code or expires_at < now:
        raise AppError("验证码错误或已过期", code=4002, status_code=400)

    record.used = True

    user = db.scalar(select(User).where(User.phone == phone))
    if not user:
        user = User(
            id=f"u_{uuid.uuid4().hex[:12]}",
            phone=phone,
            nickname=f"用户{mask_phone(phone)}",
        )
        db.add(user)

    db.commit()
    db.refresh(user)
    return user, issue_access_token(user.id)
