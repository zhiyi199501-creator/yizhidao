from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import RedirectResponse

from app.admin_spa import mount_admin_spa
from app.db import init_db
from app.errors import AppError, app_error_handler
from app.routes.admin import router as admin_router
from app.routes.admin_content import router as admin_content_router
from app.routes.ai import router as ai_router
from app.routes.auth import router as auth_router
from app.routes.cases import router as cases_router
from app.routes.feedback import router as feedback_router
from app.routes.legal import router as legal_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(
    title="易玩家 Backend",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_exception_handler(AppError, app_error_handler)
app.include_router(auth_router)
app.include_router(ai_router)
app.include_router(cases_router)
app.include_router(feedback_router)
app.include_router(legal_router)
app.include_router(admin_router)
app.include_router(admin_content_router)
mount_admin_spa(app)


@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse(url="/docs")
