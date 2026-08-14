from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import RedirectResponse

from app.db import init_db
from app.errors import AppError, app_error_handler
from app.routes.ai import router as ai_router
from app.routes.auth import router as auth_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(
    title="易知道 Backend",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_exception_handler(AppError, app_error_handler)
app.include_router(auth_router)
app.include_router(ai_router)


@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse(url="/docs")
