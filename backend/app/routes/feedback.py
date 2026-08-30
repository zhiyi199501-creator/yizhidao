from typing import Optional

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db import get_db
from app.deps import get_optional_user
from app.models import User
from app.schemas import FeedbackSubmitBody, FeedbackSubmitResponse
from app.services import feedback as feedback_store

router = APIRouter()


@router.post("/v1/feedback", response_model=FeedbackSubmitResponse)
def submit_feedback(
    body: FeedbackSubmitBody,
    db: Session = Depends(get_db),
    user: Optional[User] = Depends(get_optional_user),
) -> dict:
    feedback_store.create_feedback(
        db,
        body=body.body,
        contact=body.contact or "",
        platform=body.platform or "",
        app_version=body.appVersion or "",
        user=user,
    )
    return {"ok": True}
