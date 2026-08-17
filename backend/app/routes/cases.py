from typing import Optional

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse, Response

from app.services.case_store import all_cases, cases_version

router = APIRouter()


def _etag(version: str) -> str:
    return f'"{version}"'


def _match_etag(if_none_match: Optional[str], version: str) -> bool:
    if not if_none_match or not version:
        return False
    token = if_none_match.strip()
    if token.startswith("W/"):
        token = token[2:].strip()
    return token.strip('"') == version


@router.get("/v1/cases")
def list_cases(if_none_match: Optional[str] = Header(default=None, alias="If-None-Match")):
    version = cases_version()
    headers = {
        "ETag": _etag(version),
        "Cache-Control": "no-cache",
    }
    if _match_etag(if_none_match, version):
        return Response(status_code=304, headers=headers)
    return JSONResponse(
        content={"ok": True, "version": version, "cases": all_cases()},
        headers=headers,
    )
