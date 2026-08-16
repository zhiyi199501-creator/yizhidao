from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_env: str = "development"
    jwt_secret: str = "dev-secret-change-me"
    jwt_expire_days: int = 7
    sms_cooldown_sec: int = 60
    sms_code_expire_min: int = 5
    # mock：控制台打印；tencent：腾讯云短信
    sms_provider: str = "mock"
    # 仅 mock 生效；留空则随机码并打印
    dev_sms_fixed_code: str = "123456"
    tencent_secret_id: str = ""
    tencent_secret_key: str = ""
    tencent_sms_sdk_app_id: str = ""
    tencent_sms_sign_name: str = ""
    tencent_sms_template_id: str = ""
    tencent_sms_region: str = "ap-guangzhou"
    # code_and_minutes | code_only
    tencent_sms_template_param_mode: str = "code_and_minutes"
    database_url: str = "sqlite:///./yizhidao.db"
    ai_mode: str = "mock"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-4o-mini"
    openai_temperature: float = 0.7
    openai_timeout_sec: float = 60.0
    # 经文 JSON；默认读仓库内 App 资源，Docker 部署可指向 /app/data/Hexagrams.json
    hexagrams_path: str = ""
    # 案例 JSON；默认读 App 资源，镜像内放在 /app/app/data/cases.json（避开 data 卷覆盖）
    cases_path: str = ""
    # 生产环境若仍用 mock，必须显式打开才允许固定验证码（极不安全）
    allow_insecure_mock_sms: bool = False


settings = Settings()
