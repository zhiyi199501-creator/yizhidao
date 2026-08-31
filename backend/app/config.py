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
    # mock 或短信测试白名单生效；留空则随机码并打印
    dev_sms_fixed_code: str = "123456"
    # 逗号分隔手机号：生产也可用固定验证码，且不走真实短信
    sms_test_phones: str = "13800138000"
    tencent_secret_id: str = ""
    tencent_secret_key: str = ""
    tencent_sms_sdk_app_id: str = ""
    tencent_sms_sign_name: str = ""
    tencent_sms_template_id: str = ""
    tencent_sms_region: str = "ap-guangzhou"
    # code_and_minutes | code_only
    tencent_sms_template_param_mode: str = "code_and_minutes"
    # 阿里云号码认证（短信认证）：个人开发者可用，免签名/模板/资质申请
    aliyun_access_key_id: str = ""
    aliyun_access_key_secret: str = ""
    aliyun_sms_sign_name: str = ""          # 号码认证控制台赠送的签名
    aliyun_sms_template_code: str = ""      # 赠送模板 CODE
    aliyun_sms_region: str = "cn-beijing"
    # 模板参数 JSON：验证码用 "##code##" 占位，变量名按实际赠送模板调整
    aliyun_sms_template_param: str = '{"code":"##code##","min":"5"}'
    aliyun_sms_code_length: int = 6
    aliyun_sms_valid_sec: int = 300
    database_url: str = "sqlite:///./yizhidao.db"
    ai_mode: str = "mock"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-4o-mini"
    openai_temperature: float = 0.7
    openai_timeout_sec: float = 90.0
    # AI 限流：按登录用户；自然日 UTC+8；解读与追问共用次数
    ai_rate_interval_sec: float = 8.0
    ai_rate_daily_limit: int = 3
    ai_rate_daily_limit_unlock: int = 30
    iap_product_id: str = "com.yizhidao.app.ai.unlock"
    iap_bundle_id: str = "com.yizhidao.app"
    # mock：测试/本机解码凭证；apple：验 StoreKit 2 JWS。生产无 ALLOW_INSECURE_MOCK_IAP 时强制 apple
    iap_verify_mode: str = "mock"
    allow_insecure_mock_iap: bool = False
    # 经文 JSON；默认读仓库内 App 资源，Docker 部署可指向 /app/data/Hexagrams.json
    hexagrams_path: str = ""
    # 案例 JSON。未设时：/app/data/cases.json（data 卷热更新）→ 镜像内 /app/app/data/cases.json → 仓库 App 资源
    cases_path: str = ""
    # 黄庭书院讲解 JSON。未设时：/app/data/ImaExplanations.json → 镜像 /app/app/data/ → 仓库 App 资源
    ima_explanations_path: str = ""
    # 生产环境若仍用 mock，必须显式打开才允许固定验证码（极不安全）
    allow_insecure_mock_sms: bool = False

    # Apple / Google OAuth（逗号分隔 client id / bundle id）
    apple_client_ids: str = "com.yizhidao.app"
    google_client_ids: str = ""

    # 邮箱验证码
    email_cooldown_sec: int = 60
    email_code_expire_min: int = 5
    email_provider: str = "mock"
    dev_email_fixed_code: str = "123456"
    email_test_addresses: str = "test@example.com"
    allow_insecure_mock_email: bool = False

    # SMTP（EMAIL_PROVIDER=smtp 时必填）
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from: str = ""
    smtp_use_tls: bool = True

    # 运营后台（与 App 用户完全分开）
    admin_password: str = ""
    admin_session_days: int = 7
    # token 粗估单价（美元 / 百万 tokens）；均为 0 则看板不显示金额
    ai_usd_per_1m_prompt_tokens: float = 0.0
    ai_usd_per_1m_completion_tokens: float = 0.0

    # App「检查更新」对比的商店最新版本；发版后改 .env，不必发 App
    app_ios_latest_version: str = "1.0"
    app_android_latest_version: str = "0.1.1"
    avatars_dir: str = ""

    def apple_client_ids_list(self):
        return [part.strip() for part in self.apple_client_ids.split(",") if part.strip()]

    def google_client_ids_list(self):
        return [part.strip() for part in self.google_client_ids.split(",") if part.strip()]


settings = Settings()
