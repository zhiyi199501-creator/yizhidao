from sqlalchemy import inspect, text

from app.config import settings
from app import db as app_db


def migrate_db() -> None:
    """轻量 SQLite 迁移：扩展 users 表、兼容旧库 phone NOT NULL。"""
    if not settings.database_url.startswith("sqlite"):
        return

    engine = app_db.engine
    insp = inspect(engine)
    if not insp.has_table("users"):
        return

    cols = {c["name"]: c for c in insp.get_columns("users")}

    with engine.begin() as conn:
        if "email" not in cols:
            conn.execute(text("ALTER TABLE users ADD COLUMN email VARCHAR(255)"))
        if "apple_sub" not in cols:
            conn.execute(text("ALTER TABLE users ADD COLUMN apple_sub VARCHAR(255)"))
        if "google_sub" not in cols:
            conn.execute(text("ALTER TABLE users ADD COLUMN google_sub VARCHAR(255)"))

        phone_col = cols.get("phone", {})
        if phone_col.get("nullable") is False:
            conn.execute(
                text(
                    """
                    CREATE TABLE users_new (
                        id VARCHAR(36) PRIMARY KEY NOT NULL,
                        phone VARCHAR(20),
                        email VARCHAR(255),
                        apple_sub VARCHAR(255),
                        google_sub VARCHAR(255),
                        nickname VARCHAR(64) NOT NULL,
                        created_at DATETIME
                    )
                    """
                )
            )
            conn.execute(
                text(
                    """
                    INSERT INTO users_new (id, phone, email, apple_sub, google_sub, nickname, created_at)
                    SELECT id, phone, NULL, NULL, NULL, nickname, created_at FROM users
                    """
                )
            )
            conn.execute(text("DROP TABLE users"))
            conn.execute(text("ALTER TABLE users_new RENAME TO users"))
            conn.execute(
                text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_phone ON users (phone)")
            )
            conn.execute(
                text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_email ON users (email)")
            )
            conn.execute(
                text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_apple_sub ON users (apple_sub)")
            )
            conn.execute(
                text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_google_sub ON users (google_sub)")
            )

        existing = {
            row[1] for row in conn.execute(text("PRAGMA table_info(users)")).fetchall()
        }
        if "last_login_at" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN last_login_at DATETIME"))
        if "iap_unlocked" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_unlocked BOOLEAN DEFAULT 0"))
        if "ai_unlimited" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN ai_unlimited BOOLEAN DEFAULT 0"))
        if "iap_platform" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_platform VARCHAR(16)"))
        if "iap_product_id" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_product_id VARCHAR(128)"))
        if "iap_transaction_id" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_transaction_id VARCHAR(64)"))
        if "iap_original_transaction_id" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_original_transaction_id VARCHAR(64)"))
        if "iap_purchased_at" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN iap_purchased_at DATETIME"))
        if "avatar_updated_at" not in existing:
            conn.execute(text("ALTER TABLE users ADD COLUMN avatar_updated_at DATETIME"))
        for leftover in ("signature", "gender", "birthday", "birthday_set", "region"):
            if leftover in existing:
                conn.execute(text(f"ALTER TABLE users DROP COLUMN {leftover}"))
        conn.execute(
            text(
                "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_iap_transaction_id ON users (iap_transaction_id)"
            )
        )
