import random
import re
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional, Tuple

from jose import jwt
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.config import settings
from app.errors import AppError
from app.models import EmailCode, SMSCode, User
from app.services.email_otp import deliver_email_code, is_email_test_address
from app.services.oauth import verify_apple_identity_token, verify_google_id_token
from app.services.sms import deliver_sms_code, verify_aliyun_code

PHONE_PATTERN = re.compile(r"^1\d{10}$")
EMAIL_PATTERN = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def validate_phone(phone: str) -> str:
    normalized = phone.strip()
    if not PHONE_PATTERN.fullmatch(normalized):
        raise AppError("手机号格式不正确", code=4001, status_code=400)
    return normalized


def validate_email(email: str) -> str:
    normalized = email.strip().lower()
    if not EMAIL_PATTERN.fullmatch(normalized):
        raise AppError("邮箱格式不正确", code=4001, status_code=400)
    return normalized


def mask_phone(phone: str) -> str:
    return f"{phone[:3]}****{phone[-4:]}"


def mask_email(email: str) -> str:
    local, _, domain = email.partition("@")
    if len(local) <= 2:
        masked_local = local[0] + "*"
    else:
        masked_local = local[0] + "***" + local[-1]
    return f"{masked_local}@{domain}"


def sms_test_phone_set() -> set[str]:
    return {
        part.strip()
        for part in (settings.sms_test_phones or "").split(",")
        if part.strip()
    }


def is_sms_test_phone(phone: str) -> bool:
    return phone in sms_test_phone_set()


def issue_access_token(user_id: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(days=settings.jwt_expire_days)
    payload = {"sub": user_id, "exp": expire}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def _finish_login(db: Session, user: User) -> Tuple[User, str]:
    user.last_login_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(user)
    return user, issue_access_token(user.id)


def nickname_from_email(email: str) -> str:
    local = email.split("@", 1)[0].strip()
    if len(local) >= 2:
        return local[:20]
    if local:
        return (local + "用户")[:20]
    return "邮箱用户"


def _iso_dt(value: Optional[datetime]) -> Optional[str]:
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.isoformat()


def user_to_out(user: User) -> dict:
    from app.services.ai_rate_limit import limiter
    from app.services.iap import daily_limit_for_user

    unlimited = bool(getattr(user, "ai_unlimited", False))
    limit = daily_limit_for_user(user)
    used, remaining = limiter.snapshot(user.id, limit)
    # 限流用 limit=0；对外展示仍给买断档，避免旧客户端 max(0,1) 显示成「每天 1 次」
    display_limit = (
        int(settings.ai_rate_daily_limit_unlock)
        if unlimited or limit <= 0
        else limit
    )
    return {
        "id": user.id,
        "nickname": user.nickname,
        "phone": user.phone,
        "email": user.email,
        "createdAt": _iso_dt(user.created_at),
        "hasAvatar": user.avatar_updated_at is not None,
        "avatarUpdatedAt": _iso_dt(user.avatar_updated_at),
        "iapUnlocked": bool(user.iap_unlocked),
        "aiUnlimited": unlimited,
        "aiDailyLimit": display_limit,
        "aiDailyUsed": used,
        "aiDailyRemaining": remaining if not unlimited else 10**9,
    }


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

    is_aliyun_managed = (
        (settings.sms_provider or "mock").lower() == "aliyun"
        and not is_sms_test_phone(phone)
    )

    if is_aliyun_managed:
        code = ""
    else:
        use_fixed = bool(settings.dev_sms_fixed_code) and (
            is_sms_test_phone(phone)
            or (
                (settings.sms_provider or "mock").lower() == "mock"
                and (
                    settings.app_env.lower() != "production"
                    or settings.allow_insecure_mock_sms
                )
            )
        )
        code = settings.dev_sms_fixed_code if use_fixed else f"{random.randint(0, 999999):06d}"

    expires_at = now + timedelta(minutes=settings.sms_code_expire_min)
    db.add(SMSCode(phone=phone, code=code, expires_at=expires_at, used=False))
    db.commit()

    try:
        deliver_sms_code(phone, code)
    except AppError:
        record = db.scalar(
            select(SMSCode)
            .where(SMSCode.phone == phone, SMSCode.used.is_(False))
            .order_by(SMSCode.created_at.desc())
            .limit(1)
        )
        if record and record.code == code:
            record.used = True
            db.commit()
        raise

    return settings.sms_cooldown_sec


def send_email_code(db: Session, email: str) -> int:
    email = validate_email(email)
    now = datetime.now(timezone.utc)

    latest = db.scalar(
        select(EmailCode)
        .where(EmailCode.email == email)
        .order_by(EmailCode.created_at.desc())
        .limit(1)
    )
    if latest and latest.created_at:
        created = latest.created_at
        if created.tzinfo is None:
            created = created.replace(tzinfo=timezone.utc)
        elapsed = (now - created).total_seconds()
        if elapsed < settings.email_cooldown_sec:
            raise AppError("请求过快，请稍后再试", code=4290, status_code=429)

    use_fixed = bool(settings.dev_email_fixed_code) and (
        is_email_test_address(email)
        or (
            (settings.email_provider or "mock").lower() == "mock"
            and (
                settings.app_env.lower() != "production"
                or settings.allow_insecure_mock_email
            )
        )
    )
    code = settings.dev_email_fixed_code if use_fixed else f"{random.randint(0, 999999):06d}"

    expires_at = now + timedelta(minutes=settings.email_code_expire_min)
    db.add(EmailCode(email=email, code=code, expires_at=expires_at, used=False))
    db.commit()

    try:
        deliver_email_code(email, code)
    except AppError:
        record = db.scalar(
            select(EmailCode)
            .where(EmailCode.email == email, EmailCode.used.is_(False))
            .order_by(EmailCode.created_at.desc())
            .limit(1)
        )
        if record and record.code == code:
            record.used = True
            db.commit()
        raise

    return settings.email_cooldown_sec


def _consume_email_code(db: Session, email: str, code: str) -> None:
    email = validate_email(email)
    normalized_code = code.strip()
    if not normalized_code:
        raise AppError("验证码不能为空", code=4001, status_code=400)

    if (
        is_email_test_address(email)
        and settings.dev_email_fixed_code
        and normalized_code == settings.dev_email_fixed_code
    ):
        record = db.scalar(
            select(EmailCode)
            .where(EmailCode.email == email, EmailCode.used.is_(False))
            .order_by(EmailCode.created_at.desc())
            .limit(1)
        )
        if record:
            record.used = True
        return

    now = datetime.now(timezone.utc)
    record = db.scalar(
        select(EmailCode)
        .where(EmailCode.email == email, EmailCode.used.is_(False))
        .order_by(EmailCode.created_at.desc())
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


def send_bind_email_code(db: Session, user: User, email: str) -> int:
    if user.email:
        raise AppError("已绑定邮箱", code=4001, status_code=400)
    email = validate_email(email)
    existing = db.scalar(select(User).where(User.email == email))
    if existing and existing.id != user.id:
        raise AppError("该邮箱已被占用", code=4001, status_code=409)
    return send_email_code(db, email)


def bind_user_email(db: Session, user: User, email: str, code: str) -> User:
    if user.email:
        raise AppError("已绑定邮箱", code=4001, status_code=400)
    email = validate_email(email)
    existing = db.scalar(select(User).where(User.email == email))
    if existing and existing.id != user.id:
        raise AppError("该邮箱已被占用", code=4001, status_code=409)
    _consume_email_code(db, email, code)
    user.email = email
    db.commit()
    db.refresh(user)
    return user


def _get_or_create_user_by_phone(db: Session, phone: str) -> User:
    user = db.scalar(select(User).where(User.phone == phone))
    if not user:
        user = User(
            id=f"u_{uuid.uuid4().hex[:12]}",
            phone=phone,
            nickname=f"用户{mask_phone(phone)}",
        )
        db.add(user)
    return user


def _get_or_create_user_by_email(db: Session, email: str) -> User:
    user = db.scalar(select(User).where(User.email == email))
    desired = nickname_from_email(email)
    if not user:
        user = User(
            id=f"u_{uuid.uuid4().hex[:12]}",
            email=email,
            nickname=desired,
        )
        db.add(user)
    elif user.nickname in ("邮箱用户", f"用户{mask_email(email)}"):
        # 旧版默认昵称（脱敏邮箱）在再次登录时换成邮箱 @ 前一段。
        user.nickname = desired
    return user


def _get_or_create_user_by_apple(db: Session, apple_sub: str, nickname: Optional[str]) -> User:
    user = db.scalar(select(User).where(User.apple_sub == apple_sub))
    if not user:
        user = User(
            id=f"u_{uuid.uuid4().hex[:12]}",
            apple_sub=apple_sub,
            nickname=nickname or "Apple 用户",
        )
        db.add(user)
    elif nickname and user.nickname in ("Apple 用户", ""):
        user.nickname = nickname
    return user


def _get_or_create_user_by_google(
    db: Session,
    google_sub: str,
    email: Optional[str],
    nickname: Optional[str],
) -> User:
    user = db.scalar(select(User).where(User.google_sub == google_sub))
    if not user:
        user = User(
            id=f"u_{uuid.uuid4().hex[:12]}",
            google_sub=google_sub,
            email=email,
            nickname=nickname or (nickname_from_email(email) if email else "Google 用户"),
        )
        db.add(user)
    else:
        if email and not user.email:
            user.email = email
        if nickname and user.nickname in ("Google 用户", ""):
            user.nickname = nickname
    return user


def login_with_sms(db: Session, phone: str, code: str) -> Tuple[User, str]:
    phone = validate_phone(phone)
    normalized_code = code.strip()
    if not normalized_code:
        raise AppError("验证码不能为空", code=4001, status_code=400)

    if (
        is_sms_test_phone(phone)
        and settings.dev_sms_fixed_code
        and normalized_code == settings.dev_sms_fixed_code
    ):
        user = _get_or_create_user_by_phone(db, phone)
        record = db.scalar(
            select(SMSCode)
            .where(SMSCode.phone == phone, SMSCode.used.is_(False))
            .order_by(SMSCode.created_at.desc())
            .limit(1)
        )
        if record:
            record.used = True
        return _finish_login(db, user)

    if (
        (settings.sms_provider or "mock").lower() == "aliyun"
        and not is_sms_test_phone(phone)
    ):
        if not verify_aliyun_code(phone, normalized_code):
            raise AppError("验证码错误或已过期", code=4002, status_code=400)
        user = _get_or_create_user_by_phone(db, phone)
        return _finish_login(db, user)

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
    user = _get_or_create_user_by_phone(db, phone)
    return _finish_login(db, user)


def login_with_email(
    db: Session,
    email: str,
    code: str,
    *,
    platform: Optional[str] = None,
) -> Tuple[User, str]:
    from app.services.iap import grant_android_complimentary_unlock, normalize_client_platform

    email = validate_email(email)
    _consume_email_code(db, email, code)
    user = _get_or_create_user_by_email(db, email)
    if normalize_client_platform(platform) == "android":
        user = grant_android_complimentary_unlock(db, user)
    return _finish_login(db, user)


def login_with_apple(
    db: Session,
    identity_token: str,
    full_name: Optional[str] = None,
) -> Tuple[User, str]:
    token = identity_token.strip()
    if not token:
        raise AppError("登录凭证不能为空", code=4001, status_code=400)

    claims = verify_apple_identity_token(token)
    apple_sub = claims.get("sub")
    if not apple_sub:
        raise AppError("Apple 登录凭证无效", code=4002, status_code=401)

    nickname = full_name.strip() if full_name and full_name.strip() else None
    user = _get_or_create_user_by_apple(db, apple_sub, nickname)
    return _finish_login(db, user)


def login_with_google(db: Session, id_token: str) -> Tuple[User, str]:
    from app.services.iap import grant_android_complimentary_unlock

    token = id_token.strip()
    if not token:
        raise AppError("登录凭证不能为空", code=4001, status_code=400)

    claims = verify_google_id_token(token)
    google_sub = claims.get("sub")
    if not google_sub:
        raise AppError("Google 登录凭证无效", code=4002, status_code=401)

    email = claims.get("email")
    if isinstance(email, str):
        email = email.strip().lower() or None
    else:
        email = None
    nickname = claims.get("name")
    if isinstance(nickname, str):
        nickname = nickname.strip() or None
    else:
        nickname = None

    user = _get_or_create_user_by_google(db, google_sub, email, nickname)
    user = grant_android_complimentary_unlock(db, user)
    return _finish_login(db, user)


def update_user_profile(db: Session, user: User, nickname: Optional[str]) -> User:
    if nickname is not None:
        trimmed = nickname.strip()
        if len(trimmed) < 2 or len(trimmed) > 20:
            raise AppError("昵称须为 2–20 字", code=4001, status_code=400)
        user.nickname = trimmed
    db.commit()
    db.refresh(user)
    return user


def set_user_avatar(db: Session, user: User, data: bytes) -> User:
    from app.services.avatar import save_avatar

    try:
        save_avatar(user.id, data)
    except ValueError as exc:
        if str(exc) == "too_large":
            raise AppError("头像过大（最大 512KB）", code=4001, status_code=400) from exc
        raise AppError("请上传 JPEG 图片", code=4001, status_code=400) from exc
    user.avatar_updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(user)
    return user


def clear_user_avatar(db: Session, user: User) -> User:
    from app.services.avatar import delete_avatar

    delete_avatar(user.id)
    user.avatar_updated_at = None
    db.commit()
    db.refresh(user)
    return user


def delete_user_account(db: Session, user: User) -> None:
    from app.services.avatar import delete_avatar

    delete_avatar(user.id)
    if user.phone:
        db.execute(delete(SMSCode).where(SMSCode.phone == user.phone))
    if user.email:
        db.execute(delete(EmailCode).where(EmailCode.email == user.email))
    db.delete(user)
    db.commit()
