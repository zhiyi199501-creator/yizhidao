from jose import JWTError, jwt

from app.config import settings
from app.errors import AppError


def decode_access_token(token: str) -> str:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except JWTError as exc:
        raise AppError("登录态无效", code=4003, status_code=401) from exc

    user_id = payload.get("sub")
    if not user_id or not isinstance(user_id, str):
        raise AppError("登录态无效", code=4003, status_code=401)
    return user_id
