# 易知道 Backend

FastAPI 最小后端：手机号验证码登录 + AI 解读，接口对齐 `docs/backend-min-spec.md`。

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

## 开发期验证码（mock）

默认 `SMS_PROVIDER=mock`，`.env` 中 `DEV_SMS_FIXED_CODE=123456`，联调时直接填 `123456`。

控制台会打印：

```text
[sms:mock] phone=138****8000 code=123456
```

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

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/v1/auth/sms/send` | 发送验证码 |
| POST | `/v1/auth/sms/login` | 验证码登录 |
| GET | `/v1/me` | 当前用户（需 Bearer token） |
| POST | `/v1/ai/analyze` | AI 解读（需 Bearer token） |
| POST | `/v1/ai/followup` | AI 追问 / 补充背景（需 Bearer token） |

## 与 iOS 联调

1. 启动本后端（端口 `8080`）
2. iOS **Debug** 模拟器请求 `http://127.0.0.1:8080`
3. **真机 Release** 走 `https://yizhidao.codedance.work`（见 `AuthAPI` `#else` 分支）；Debug 真机仍用局域网 IP

### curl 自测

```bash
curl -X POST http://127.0.0.1:8080/v1/auth/sms/send \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000"}'

curl -X POST http://127.0.0.1:8080/v1/auth/sms/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000","code":"123456"}'
```

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
│   └── services/
│       ├── auth.py          # 验证码与 JWT
│       ├── sms.py           # mock / 腾讯云发送
│       ├── token.py         # token 校验
│       ├── hexagram_store.py# 读 App 侧 Hexagrams.json
│       ├── case_store.py    # 读 App 侧 cases.json
│       └── ai.py            # mock / openai 解读、追问与提示词
├── requirements.txt
├── .env.example
├── Dockerfile / docker-compose.yml / docker-compose.prod.yml
├── Caddyfile
└── start.sh
```

## 接入真实模型

App 端不用改，只改后端 `.env`：

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

`AI_MODE=mock` 时走规则解读（不耗 token）。

解读框架（提示词）：**卦辞→事情背景；大象辞→宜努力方向；动爻爻辞/小象→当下情形**；并附该本卦初爻至上爻讲习案例作取象参照。实现见 `app/services/ai.py`。追问走 `POST /v1/ai/followup`。

经文来源：`Yizhidao/Resources/Hexagrams.json`。案例来源：`Yizhidao/Resources/cases.json`（镜像内 `/app/app/data/cases.json`）。

## 生产部署

见仓库文档：[docs/deploy.md](../docs/deploy.md)

简要（独占服务器）：

```bash
cd backend
cp .env.example .env   # 改 JWT_SECRET 等
docker compose up -d --build
```

与已有系统 Caddy 共用 80/443 时用 `docker compose -f docker-compose.prod.yml up -d --build`（详见 `docs/deploy.md`）。

**现役**：`https://yizhidao.codedance.work`（DNS A → `43.128.104.104`）。

## 下一步

- 开通企业主体后再配腾讯云短信 / 微信登录
- App Store 与文言讲解层（产品侧）
