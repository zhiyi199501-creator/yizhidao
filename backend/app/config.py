from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "development"
    jwt_secret: str = "dev-secret-change-me"
    jwt_expire_days: int = 7
    sms_cooldown_sec: int = 60
    sms_code_expire_min: int = 5
    dev_sms_fixed_code: str = "123456"
    database_url: str = "sqlite:///./yizhidao.db"
    ai_mode: str = "mock"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-4o-mini"
    openai_temperature: float = 0.7
    openai_timeout_sec: float = 60.0


settings = Settings()
