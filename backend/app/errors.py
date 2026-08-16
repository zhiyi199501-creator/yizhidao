from fastapi import Request
from fastapi.responses import JSONResponse


class AppError(Exception):
    def __init__(self, message: str, code: int, status_code: int = 400):
        self.message = message
        self.code = code
        self.status_code = status_code


async def app_error_handler(_: Request, exc: AppError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"message": exc.message, "code": exc.code},
    )
