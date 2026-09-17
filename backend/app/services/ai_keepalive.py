"""Send whitespace keep-alives so 20s App idle timeouts do not abort a long analyze."""

from __future__ import annotations

import json
import logging
import queue
import threading
from typing import Callable, Iterator

from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.errors import AppError

logger = logging.getLogger(__name__)

_KEEPALIVE = b"\n"
_INTERVAL_SEC = 4.0


def stream_json(build: Callable[[], BaseModel]) -> StreamingResponse:
    return StreamingResponse(_chunks(build), media_type="application/json")


def _chunks(build: Callable[[], BaseModel]) -> Iterator[bytes]:
    box: queue.Queue = queue.Queue(maxsize=1)

    def worker() -> None:
        try:
            box.put(("ok", build()))
        except BaseException as exc:  # noqa: BLE001 — must surface AppError to the client
            box.put(("err", exc))

    threading.Thread(target=worker, daemon=True).start()
    yield _KEEPALIVE
    while True:
        try:
            kind, value = box.get(timeout=_INTERVAL_SEC)
            break
        except queue.Empty:
            yield _KEEPALIVE
    if kind == "err":
        yield _error_bytes(value)
        return
    yield value.model_dump_json().encode("utf-8")


def _error_bytes(exc: BaseException) -> bytes:
    if isinstance(exc, AppError):
        payload = {"ok": False, "message": exc.message, "code": exc.code}
    else:
        logger.exception("[ai] keepalive worker failed: %s", exc)
        payload = {"ok": False, "message": "解读没有完成，请稍后重试", "code": 5000}
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")
