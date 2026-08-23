"""隐私政策与用户协议等静态法律页面的路由。"""

from pathlib import Path

from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter()

_TPL_DIR = Path(__file__).resolve().parent.parent / "templates"

_PRIVACY_HTML = (_TPL_DIR / "privacy_policy.html").read_text(encoding="utf-8")
_TERMS_HTML = (_TPL_DIR / "terms_of_service.html").read_text(encoding="utf-8")
_SUPPORT_HTML = (_TPL_DIR / "support.html").read_text(encoding="utf-8")


@router.get("/privacy", include_in_schema=False)
def privacy_policy() -> HTMLResponse:
    """隐私政策页面（App Store 必填的隐私政策 URL）。"""
    return HTMLResponse(_PRIVACY_HTML)


@router.get("/terms", include_in_schema=False)
def terms_of_service() -> HTMLResponse:
    """用户协议页面。"""
    return HTMLResponse(_TERMS_HTML)


@router.get("/support", include_in_schema=False)
def support_page() -> HTMLResponse:
    """App Store Support URL。"""
    return HTMLResponse(_SUPPORT_HTML)
