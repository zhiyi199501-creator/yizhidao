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

## 开发期验证码

默认 `.env` 中 `DEV_SMS_FIXED_CODE=123456`，联调时直接填 `123456` 即可。

服务端也会在控制台打印：

```text
[sms] phone=138****8000 code=123456 expires=...
```

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/v1/auth/sms/send` | 发送验证码 |
| POST | `/v1/auth/sms/login` | 验证码登录 |
| POST | `/v1/ai/analyze` | AI 解读（需 Bearer token） |

## 与 iOS 联调

1. 启动本后端（端口 `8080`）
2. iOS **Debug** 模拟器请求 `http://127.0.0.1:8080`
3. **真机** 与 Mac 连同一 Wi‑Fi/热点；改 `YizhidaoApp.swift` 中 `AuthAPI` 真机 `baseURL` 为局域网 IP（`ipconfig getifaddr en0`）

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
│       ├── token.py         # token 校验
│       ├── hexagram_store.py# 读 App 侧 Hexagrams.json
│       └── ai.py            # mock / openai 解读与提示词
├── requirements.txt
├── .env.example
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

解读框架（提示词）：**卦辞→事情背景；大象辞→宜努力方向；动爻爻辞/小象→当下情形**。实现见 `app/services/ai.py`。

经文来源：自动读取 `Yizhidao/Resources/Hexagrams.json`。

## 下一步

- 接入真实短信服务商
- 增加 `GET /v1/me` 校验 token
- 部署到 `https://api.yizhidao.app`
