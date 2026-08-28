from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_current_user_id
from app.schemas import AIAnalysisBody, AIAnalysisResponse, AIFollowupBody, AIFollowupResponse
from app.services.ai import analyze_reading, followup_reading
from app.services.ai_usage import run_logged_ai

router = APIRouter()


@router.post("/v1/ai/analyze", response_model=AIAnalysisResponse)
def ai_analyze(
    body: AIAnalysisBody,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> AIAnalysisResponse:
    analysis, usage = run_logged_ai(
        db,
        user_id=user_id,
        kind="analyze",
        method=body.method,
        fn=lambda: analyze_reading(body),
    )
    return AIAnalysisResponse(analysis=analysis, usage=usage)


@router.post("/v1/ai/followup", response_model=AIFollowupResponse)
def ai_followup(
    body: AIFollowupBody,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
) -> AIFollowupResponse:
    def _run():
        reply, advice, ask_next, usage = followup_reading(body)
        return (reply, advice, ask_next), usage

    (reply, advice, ask_next), usage = run_logged_ai(
        db,
        user_id=user_id,
        kind="followup",
        method=body.method,
        fn=_run,
    )
    return AIFollowupResponse(reply=reply, advice=advice, askNext=ask_next, usage=usage)
