import hashlib
import hmac
from datetime import datetime, timedelta, timezone

from jose import JWTError, jwt

from app.config import settings
from app.errors import AppError

ADMIN_COOKIE = "admin_session"


def admin_configured() -> bool:
    return bool(settings.admin_password)


def _password_matches(given: str) -> bool:
    expected = settings.admin_password or ""
    left = hashlib.sha256(given.encode("utf-8")).digest()
    right = hashlib.sha256(expected.encode("utf-8")).digest()
    return hmac.compare_digest(left, right)


def issue_admin_token() -> str:
    expire = datetime.now(timezone.utc) + timedelta(days=max(1, int(settings.admin_session_days)))
    payload = {"sub": "admin", "role": "admin", "exp": expire}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def verify_admin_token(token: str) -> None:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except JWTError as exc:
        raise AppError("登录态无效", code=4003, status_code=401) from exc
    if payload.get("role") != "admin" or payload.get("sub") != "admin":
        raise AppError("登录态无效", code=4003, status_code=401)


def login_admin(password: str) -> str:
    if not admin_configured():
        raise AppError("后台未配置管理员密码", code=4001, status_code=503)
    if not _password_matches(password):
        raise AppError("密码错误", code=4002, status_code=401)
    return issue_admin_token()


def cookie_secure() -> bool:
    return settings.app_env.lower() == "production"


def set_admin_cookie(response, token: str) -> None:
    response.set_cookie(
        key=ADMIN_COOKIE,
        value=token,
        httponly=True,
        secure=cookie_secure(),
        samesite="lax",
        max_age=int(settings.admin_session_days) * 24 * 60 * 60,
        path="/",
    )


def clear_admin_cookie(response) -> None:
    response.delete_cookie(key=ADMIN_COOKIE, path="/")
