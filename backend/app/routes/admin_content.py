import json
from typing import Optional

from fastapi import APIRouter, Depends, File, Query, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.db import get_db
from app.errors import AppError
from app.routes.admin import require_admin
from app.services import admin_eval, content_cases
from app.services.case_xlsx import parse_cases_xlsx
from app.services.hexagram_store import hexagram_catalog, hexagram_reading
from app.services.ima_store import entries_for_hexagram, ima_hexagram_index, save_entry_answer

router = APIRouter()


class CaseBody(BaseModel):
    file: str = Field(min_length=1, max_length=128)
    hexagram: str = Field(min_length=1, max_length=32)
    position: str = Field(min_length=1, max_length=64)
    background: str = ""
    question: str = ""
    casting: str = ""
    explanation: str = ""
    verification: str = ""


class ImaAnswerBody(BaseModel):
    answer: str = Field(max_length=200_000)


class EvalRunBody(BaseModel):
    ids: Optional[list[str]] = None
    live: bool = False


@router.get("/v1/admin/cases")
def admin_cases(
    q: str = "",
    number: Optional[int] = None,
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=30, ge=1, le=100),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return content_cases.list_cases(db, q=q, number=number, page=page, page_size=pageSize)


@router.get("/v1/admin/cases/status")
def admin_cases_status(_: str = Depends(require_admin), db: Session = Depends(get_db)) -> dict:
    return content_cases.status(db)


@router.get("/v1/admin/cases/export")
def admin_cases_export(_: str = Depends(require_admin), db: Session = Depends(get_db)) -> Response:
    payload = json.dumps(content_cases.export_items(db), ensure_ascii=False, indent=1)
    return Response(
        content=payload,
        media_type="application/json; charset=utf-8",
        headers={"Content-Disposition": 'attachment; filename="cases.json"'},
    )


@router.post("/v1/admin/cases/publish")
def admin_cases_publish(_: str = Depends(require_admin), db: Session = Depends(get_db)) -> dict:
    return content_cases.publish(db)


@router.post("/v1/admin/cases/import")
async def admin_cases_import(
    file: UploadFile = File(...),
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    raw = await file.read()
    name = (file.filename or "").lower()
    problems: list[str] = []
    if name.endswith(".xlsx"):
        items, problems = parse_cases_xlsx(raw)
    else:
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise AppError("无法解析 JSON", code=4001, status_code=400) from exc
        items = parsed if isinstance(parsed, list) else []
    result = content_cases.replace_all(db, items)
    result["problems"] = problems
    return result


@router.post("/v1/admin/cases")
def admin_cases_create(
    body: CaseBody,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return content_cases.create_case(db, body.model_dump())


@router.get("/v1/admin/cases/{case_id}")
def admin_case_get(
    case_id: int, _: str = Depends(require_admin), db: Session = Depends(get_db)
) -> dict:
    return content_cases.get_case(db, case_id)


@router.put("/v1/admin/cases/{case_id}")
def admin_case_update(
    case_id: int,
    body: CaseBody,
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return content_cases.update_case(db, case_id, body.model_dump())


@router.delete("/v1/admin/cases/{case_id}")
def admin_case_delete(
    case_id: int, _: str = Depends(require_admin), db: Session = Depends(get_db)
) -> dict:
    return content_cases.delete_case(db, case_id)


@router.get("/v1/admin/ima")
def admin_ima_index(_: str = Depends(require_admin)) -> dict:
    return {"ok": True, "hexagrams": ima_hexagram_index()}


@router.get("/v1/admin/ima/{number}")
def admin_ima_hexagram(number: int, _: str = Depends(require_admin)) -> dict:
    if number < 1 or number > 64:
        raise AppError("卦号无效", code=4001, status_code=400)
    return {"ok": True, "number": number, "entries": entries_for_hexagram(number)}


@router.put("/v1/admin/ima/entries/{entry_id}")
def admin_ima_save(
    entry_id: str, body: ImaAnswerBody, _: str = Depends(require_admin)
) -> dict:
    saved = save_entry_answer(entry_id, body.answer)
    return {
        "ok": True,
        "entry": saved,
        "note": "保存后服务端 AI 立刻用新稿；App 点经文弹层要下次发版。",
    }


@router.get("/v1/admin/hexagrams")
def admin_hexagrams(_: str = Depends(require_admin)) -> dict:
    return {"ok": True, "hexagrams": hexagram_catalog()}


@router.get("/v1/admin/hexagrams/{number}")
def admin_hexagram(number: int, _: str = Depends(require_admin)) -> dict:
    item = hexagram_reading(number)
    if not item:
        raise AppError("经文未加载", code=4001, status_code=404)
    return {"ok": True, "hexagram": item}


@router.get("/v1/admin/eval/samples")
def admin_eval_samples(_: str = Depends(require_admin)) -> dict:
    return {"ok": True, "samples": admin_eval.list_samples()}


@router.post("/v1/admin/eval/run")
def admin_eval_run(body: EvalRunBody, _: str = Depends(require_admin)) -> dict:
    return admin_eval.run_eval(body.ids, body.live)
