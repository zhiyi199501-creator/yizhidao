from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings


class Base(DeclarativeBase):
    pass


def _connect_args(url: str) -> dict:
    return {"check_same_thread": False} if url.startswith("sqlite") else {}


engine = create_engine(settings.database_url, connect_args=_connect_args(settings.database_url))
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def reset_engine() -> None:
    """测试或改 DATABASE_URL 后重建连接。"""
    global engine, SessionLocal
    engine.dispose()
    url = settings.database_url
    engine = create_engine(url, connect_args=_connect_args(url))
    SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    from app import models  # noqa: F401
    from app.migrate import migrate_db

    Base.metadata.create_all(bind=engine)
    migrate_db()
