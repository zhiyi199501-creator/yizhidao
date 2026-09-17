from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user_id
from app.schemas import AIAnalysisBody, AIAnalysisResponse, AIFollowupBody, AIFollowupResponse
from app.services.ai import analyze_reading, followup_reading
from app.services.ai_keepalive import stream_json
from app.services.ai_usage import begin_ai_call, finish_ai_call, run_logged_ai

router = APIRouter()


@router.post("/v1/ai/analyze")
def ai_analyze(
    body: AIAnalysisBody,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> StreamingResponse:
    begin_ai_call(db, user_id, "analyze", body.method)

    def _build() -> AIAnalysisResponse:
        try:
            analysis, usage = run_logged_ai(
                db,
                user_id=user_id,
                kind="analyze",
                method=body.method,
                fn=lambda: analyze_reading(body),
                already_acquired=True,
            )
            return AIAnalysisResponse(analysis=analysis, usage=usage)
        finally:
            finish_ai_call(user_id)

    return stream_json(_build)


@router.post("/v1/ai/followup")
def ai_followup(
    body: AIFollowupBody,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> StreamingResponse:
    begin_ai_call(db, user_id, "followup", body.method)

    def _run():
        reply, advice, ask_next, usage = followup_reading(body)
        return (reply, advice, ask_next), usage

    def _build() -> AIFollowupResponse:
        try:
            (reply, advice, ask_next), usage = run_logged_ai(
                db,
                user_id=user_id,
                kind="followup",
                method=body.method,
                fn=_run,
                already_acquired=True,
            )
            return AIFollowupResponse(reply=reply, advice=advice, askNext=ask_next, usage=usage)
        finally:
            finish_ai_call(user_id)

    return stream_json(_build)
