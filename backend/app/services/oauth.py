import logging
from functools import lru_cache

import httpx
from jose import jwk, jwt
from jose.exceptions import JWTError

from app.config import settings
from app.errors import AppError

logger = logging.getLogger(__name__)

APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys"
GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
APPLE_ISSUER = "https://appleid.apple.com"
GOOGLE_ISSUERS = ("accounts.google.com", "https://accounts.google.com")


@lru_cache(maxsize=4)
def _fetch_jwks(url: str) -> dict:
    with httpx.Client(timeout=10) as client:
        resp = client.get(url)
        resp.raise_for_status()
        return resp.json()


def _get_signing_key(token: str, jwks_url: str):
    headers = jwt.get_unverified_header(token)
    kid = headers.get("kid")
    jwks = _fetch_jwks(jwks_url)
    for key in jwks.get("keys", []):
        if key.get("kid") == kid:
            return jwk.construct(key)
    raise AppError("无法验证登录凭证", code=4002, status_code=401)


def verify_apple_identity_token(identity_token: str) -> dict:
    client_ids = settings.apple_client_ids_list()
    if not client_ids:
        raise AppError("Apple 登录未配置", code=5000, status_code=503)

    key = _get_signing_key(identity_token, APPLE_JWKS_URL)
    last_error: Exception | None = None
    for aud in client_ids:
        try:
            return jwt.decode(
                identity_token,
                key,
                algorithms=["RS256"],
                audience=aud,
                issuer=APPLE_ISSUER,
                options={"verify_at_hash": False},
            )
        except JWTError as exc:
            last_error = exc
            continue
    logger.warning("apple token verify failed: %s", last_error)
    raise AppError("Apple 登录凭证无效", code=4002, status_code=401)


def verify_google_id_token(id_token: str) -> dict:
    client_ids = settings.google_client_ids_list()
    if not client_ids:
        raise AppError("Google 登录未配置", code=5000, status_code=503)

    key = _get_signing_key(id_token, GOOGLE_JWKS_URL)
    last_error: Exception | None = None
    for aud in client_ids:
        try:
            return jwt.decode(
                id_token,
                key,
                algorithms=["RS256"],
                audience=aud,
                issuer=GOOGLE_ISSUERS,
            )
        except JWTError as exc:
            last_error = exc
            continue
    logger.warning("google token verify failed: %s", last_error)
    raise AppError("Google 登录凭证无效", code=4002, status_code=401)
