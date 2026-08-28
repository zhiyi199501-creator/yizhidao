from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles


def _admin_dist() -> Path:
    docker_path = Path("/app/admin_dist")
    if docker_path.exists():
        return docker_path
    return Path(__file__).resolve().parents[2] / "admin" / "dist"


def mount_admin_spa(app: FastAPI) -> None:
    dist = _admin_dist()
    index = dist / "index.html"
    if not index.exists():
        @app.get("/admin", include_in_schema=False)
        @app.get("/admin/{path:path}", include_in_schema=False)
        def admin_placeholder(path: str = ""):
            return JSONResponse(
                {
                    "ok": False,
                    "message": "管理后台前端未构建。本地请在 admin/ 执行 npm run dev，或 npm run build 后重启后端。",
                }
            )

        return

    assets = dist / "assets"
    if assets.is_dir():
        app.mount("/admin/assets", StaticFiles(directory=assets), name="admin-assets")

    @app.get("/admin", include_in_schema=False)
    @app.get("/admin/{path:path}", include_in_schema=False)
    def admin_index(path: str = ""):
        if path:
            candidate = (dist / path).resolve()
            try:
                candidate.relative_to(dist.resolve())
            except ValueError:
                return FileResponse(index)
            if candidate.is_file():
                return FileResponse(candidate)
        return FileResponse(index)
