# 易玩家 Backend

FastAPI 最小后端：Apple / Google / 邮箱 OTP 登录 + AI 解读，接口对齐 `docs/backend-min-spec.md`。短信路由仍保留，现役 App 登录页不展示。

## 要求

- Python 3.9+
- 建议使用虚拟环境（`start.sh` 会自动创建）

## 快速启动

```bash
./start.sh
```

或在仓库根目录：

```bash
./start-backend.sh
```

脚本会自动：创建虚拟环境、安装依赖、复制 `.env`、启动服务。

启动后：

- 根路径：`http://127.0.0.1:8080/` → 重定向到 `/docs`
- 健康检查：`GET http://127.0.0.1:8080/health`
- 接口文档：`http://127.0.0.1:8080/docs`

## 开发期邮箱验证码（mock）

默认 `EMAIL_PROVIDER=mock`。改 `.env` 后必须重启后端。

- `DEV_EMAIL_FIXED_CODE` 有值：开发环境任意合法邮箱都用该码（不必发信）
- 为空：每次随机 6 位，终端打印（与短信一样用 `print`，uvicorn 默认看不到 `logger.info`）：

```text
[email:mock] to=t***t@example.com code=482193
```

白名单 `EMAIL_TEST_ADDRESSES`（逗号分隔）在 **smtp 生产**下也不发真邮件、只用固定码。**审核包不要配白名单**。生产用 `EMAIL_PROVIDER=smtp` + 空白名单 + 空固定码。

## 开发期短信验证码（mock，App 无入口）

默认 `SMS_PROVIDER=mock`，`.env` 中 `DEV_SMS_FIXED_CODE=123456`，curl 联调可填 `123456`。

控制台会打印：

```text
[sms:mock] phone=138****8000 code=123456
```

生产（`APP_ENV=production`）默认对普通号发**随机码**并打日志。白名单 `SMS_TEST_PHONES`（逗号分隔，如 `13800138000`）仍用固定码且不走腾讯云；日志为 `[sms:test]`。不要用 `ALLOW_INSECURE_MOCK_SMS=true` 放开全站固定码。

## 接入腾讯云短信（真实发送）

1. 打开 [腾讯云短信控制台](https://console.cloud.tencent.com/smsv2)
2. 创建应用，记下 **SmsSdkAppId**（形如 `1400xxxxxx`）
3. 创建签名（如「易知道」），等待审核通过
4. 创建**验证码**模板，例如：
   - 双变量：`您的验证码为{1}，{2}分钟内有效。` → `TENCENT_SMS_TEMPLATE_PARAM_MODE=code_and_minutes`
   - 单变量：`您的验证码为{1}，请勿泄露。` → `TENCENT_SMS_TEMPLATE_PARAM_MODE=code_only`
5. 在访问管理创建 API 密钥，拿到 **SecretId / SecretKey**（只放服务端）
6. 编辑 `backend/.env`：

```bash
SMS_PROVIDER=tencent
DEV_SMS_FIXED_CODE=
TENCENT_SECRET_ID=AKIDxxxx
TENCENT_SECRET_KEY=xxxx
TENCENT_SMS_SDK_APP_ID=1400xxxxxx
TENCENT_SMS_SIGN_NAME=易知道
TENCENT_SMS_TEMPLATE_ID=1234567
TENCENT_SMS_REGION=ap-guangzhou
TENCENT_SMS_TEMPLATE_PARAM_MODE=code_and_minutes
```

7. 安装依赖并重启：

```bash
./start-backend.sh
```

发送失败时接口会返回错误信息，且该次验证码作废。

## 接入阿里云号码认证短信（个人开发者推荐）

验证码由阿里云生成与核验；比腾讯云自定义签名更容易通过个人审核。

1. 打开 [号码认证控制台](https://dypns.console.aliyun.com/)，开通「短信认证」
2. 使用控制台**赠送的签名与模板**（或自建并审核通过）
3. 在 RAM 创建 AccessKey，拿到 **AccessKey ID / Secret**
4. 编辑 `backend/.env`：

```bash
SMS_PROVIDER=aliyun
DEV_SMS_FIXED_CODE=
SMS_TEST_PHONES=13800138000
ALIYUN_ACCESS_KEY_ID=LTAIxxxx
ALIYUN_ACCESS_KEY_SECRET=xxxx
ALIYUN_SMS_SIGN_NAME=速通互联验证码
ALIYUN_SMS_TEMPLATE_CODE=100001
ALIYUN_SMS_TEMPLATE_PARAM={"code":"##code##","min":"5"}
ALIYUN_SMS_CODE_LENGTH=6
ALIYUN_SMS_VALID_SEC=300
```

5. 重启后端（Docker 需确保镜像含 `alibabacloud_dypnsapi20170525`，见 `requirements.txt`）

白名单 `SMS_TEST_PHONES` 仍走固定码，不发真实短信。

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/v1/auth/sms/send` | 发送短信验证码（Debug 白名单） |
| POST | `/v1/auth/sms/login` | 短信验证码登录 |
| POST | `/v1/auth/email/send` | 发送邮箱验证码 |
| POST | `/v1/auth/email/login` | 邮箱验证码登录 |
| POST | `/v1/auth/apple` | Sign in with Apple |
| POST | `/v1/auth/google` | Google 登录 |
| GET | `/v1/me` | 当前用户（需 Bearer token） |
| DELETE | `/v1/me` | 注销账号（需 Bearer token） |
| GET | `/privacy` `/terms` `/support` | 法律与支持页（HTML） |
| GET | `/v1/cases` | 案例列表（公开；支持 `If-None-Match`） |
| POST | `/v1/ai/analyze` | AI 解读（需 Bearer token） |
| POST | `/v1/ai/followup` | AI 追问 / 补充背景（需 Bearer token） |

## 与 App 联调

1. 启动本后端（端口 `8080`）
2. iOS **Debug** 模拟器请求 `http://127.0.0.1:8080`；安卓 Debug 见 `android/app/build.gradle.kts`
3. **真机 Release** 走 `https://api.yiwanjia.work`（仅海外）。安卓不要用 `HttpURLConnection` 或 `addQuicHint`，见 `docs/deploy.md`。

### curl 自测

```bash
# 邮箱（mock：固定码或看终端 [email:mock]）
curl -X POST http://127.0.0.1:8080/v1/auth/email/send \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com"}'

curl -X POST http://127.0.0.1:8080/v1/auth/email/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","code":"123456"}'

# 短信（Debug 白名单 13800138000 / 123456）
curl -X POST http://127.0.0.1:8080/v1/auth/sms/send \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000"}'

curl -X POST http://127.0.0.1:8080/v1/auth/sms/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000","code":"123456"}'
```

## 海外 OAuth / 邮箱

`.env` 关键项（见 `.env.example`）：

| 变量 | 说明 |
|------|------|
| `APPLE_CLIENT_IDS` | 逗号分隔；默认 `com.yizhidao.app` |
| `GOOGLE_CLIENT_IDS` | Google Cloud OAuth Client ID（iOS / Android / Web，逗号分隔） |
| `EMAIL_PROVIDER` | `mock`（终端 `[email:mock]`）或 `smtp`（Resend 等） |
| `EMAIL_TEST_ADDRESSES` | 白名单：固定码且不发信；审核 / 生产公网应留空 |
| `DEV_EMAIL_FIXED_CODE` | 测试固定码；生产应留空 |
| `SMTP_*` | `smtp` 时必填；Resend：`smtp.resend.com:587`、`SMTP_USER=resend`、密码为 API Key、`SMTP_USE_TLS=true`（勿用 465） |

控制台：

1. **Apple Developer** → App ID `com.yizhidao.app` → Sign in with Apple（iOS）
2. **Google Cloud** → OAuth：Android Client（包名 + SHA-1）+ Web Client（Credential Manager 与后端验 `aud`）
3. **Android App**：`android/app/build.gradle.kts` 的 `GOOGLE_WEB_CLIENT_ID` 填 Web Client ID
4. **后端**：`.env` 的 `GOOGLE_CLIENT_IDS` 填入上述 Web / Android Client ID（逗号分隔）；`APPLE_CLIENT_IDS` 默认 `com.yizhidao.app`

## 目录

```text
backend/
├── app/
│   ├── main.py              # FastAPI 入口
│   ├── config.py            # 环境配置
│   ├── db.py / models.py    # SQLite 用户与验证码
│   ├── schemas.py / errors.py / deps.py
│   ├── routes/auth.py       # 登录
│   ├── routes/ai.py         # AI 解读
│   ├── routes/cases.py      # 案例列表热更新
│   └── services/
│       ├── auth.py          # 验证码与 JWT
│       ├── sms.py           # mock / 腾讯云 / 阿里云号码认证
│       ├── token.py         # token 校验
│       ├── hexagram_store.py# 读 App 侧 Hexagrams.json
│       ├── case_store.py    # 读 App 侧 cases.json
│       ├── ima_store.py     # 读 App 侧 ImaExplanations.json
│       ├── ima_format.py    # 讲解清洗（去思考过程／脚注）
│       └── ai.py            # mock / openai 解读、追问与提示词
│   ├── routes/
│   │   ├── auth.py          # 登录、/v1/me、注销
│   │   ├── legal.py         # /privacy /terms /support
│   │   ├── ai.py / cases.py
│   └── templates/           # 隐私政策、用户协议、支持页 HTML
├── requirements.txt
├── .env.example
├── Dockerfile / docker-compose.yml / docker-compose.prod.yml
├── Caddyfile
└── start.sh
```

## 接入真实模型

App 端不用改，只改后端 `.env`。`AI_MODE=openai` 表示走 **OpenAI 兼容** Chat Completions，不表示必须用 OpenAI；`OPENAI_BASE_URL` / `OPENAI_MODEL` 以 `.env` 为准（本机常用 DeepSeek）。

```bash
AI_MODE=openai
OPENAI_API_KEY=你的密钥
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o-mini
```

**DeepSeek 示例：**

```bash
AI_MODE=openai
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-chat
```

`AI_MODE=mock` 时走规则解读（不耗 token）。解读机制见 [`docs/ai-reading.md`](../docs/ai-reading.md)；接口合同见 `docs/backend-min-spec.md`。单测：`.venv/bin/python -m unittest`。本机抽检：`.venv/bin/python scripts/eval_ai_reading.py`（`--dry-run` 不调模型）。AI 限流：登录用户、解读与追问合计自然日 40 次（UTC+8）、间隔 8 秒；`AI_RATE_*` 见 `.env.example`。

经文来源：`ios/Yizhidao/Resources/Hexagrams.json`。讲解来源：同目录 `ImaExplanations.json`（镜像 `/app/app/data/`）。案例来源：`cases.json`。App 用 `GET /v1/cases` 热更新（2026-08-17 已部署）。镜像内 `/app/app/data/cases.json`；若 data 卷存在 `/app/data/cases.json` 则优先。

## 生产部署

见仓库文档：[docs/deploy.md](../docs/deploy.md)

简要（独占服务器，**新加坡现役**）：

```bash
cd backend
cp .env.example .env   # 改 JWT_SECRET 等
cp Caddyfile.overseas Caddyfile   # 仓库根 Caddyfile 是国内 yzd，勿直接拿去起新加坡
docker compose up -d --build
```

与已有**系统 Caddy** 共用 80/443 时（旧海外 / 国内遗留机）才用 `docker compose -f docker-compose.prod.yml`。详见 `docs/deploy.md`。

**现役（App Release）**：`https://api.yiwanjia.work`（新加坡 `124.156.192.137`，SSH `yiwanjia`；仅海外上架）。国内 `yzd.codedance.work` 为遗留机，App 不连。运维见 `docs/deploy.md`。

## 下一步

- App Store：Connect **排除中国大陆**；法律 URL → `https://api.yiwanjia.work/{privacy,terms,support}`；TestFlight → 截屏与元数据 → 提审；品牌名「易玩家」
- 生产 `.env`：SMTP 已接 Resend（`EMAIL_PROVIDER=smtp`）；`GOOGLE_CLIENT_IDS` 与 App `GOOGLE_WEB_CLIENT_ID` 仍待填。新加坡现役是默认 `docker compose`（api + Caddy）；勿改成 `docker-compose.prod.yml`
- （可选）国内遗留机仅运维对照；不上中国区则无需 ICP；App 基址保持 `api.yiwanjia.work`
