# 易知道最小后端接口（登录 + AI + 案例热更新）

本文件是现役接口合同（路径与字段）。App Release 基址为 `https://yzh.codedance.work`。`GET /v1/cases` 于 2026-08-17 上线；同一 FastAPI 也挂在 `yizhidao.codedance.work`（部分 iPhone 11 不可用该旧名）。

## 目标
- 客户端不直连大模型，密钥留在服务端
- 登录与 AI 分析统一走后端，便于限流、审计、计费控制

## 鉴权
- Header: `Authorization: Bearer <access_token>`
- Token 由手机号/微信登录后签发

## 接口

### 1) 发送短信验证码
- `POST /v1/auth/sms/send`
- req:
```json
{ "phone": "13800138000" }
```
- resp:
```json
{ "ok": true, "cooldownSec": 60 }
```

### 2) 手机号验证码登录
- `POST /v1/auth/sms/login`
- req:
```json
{ "phone": "13800138000", "code": "123456" }
```
- resp:
```json
{
  "ok": true,
  "accessToken": "jwt-or-session-token",
  "user": { "id": "u_123", "nickname": "用户138****8000", "phone": "13800138000" }
}
```

### 3) 当前用户（校验登录态）
- `GET /v1/me`
- Header: `Authorization: Bearer <access_token>`
- resp:
```json
{
  "ok": true,
  "user": { "id": "u_123", "nickname": "用户138****8000", "phone": "13800138000" }
}
```
- 无效/过期 token → HTTP 401，`code: 4003`

### 4) 微信登录（未实现，接口预留）
- `POST /v1/auth/wechat/login`
- req:
```json
{ "code": "wx_auth_code" }
```
- resp:
```json
{
  "ok": true,
  "accessToken": "jwt-or-session-token",
  "user": { "id": "u_456", "nickname": "微信用户", "phone": null }
}
```

### 5) AI 解读
- `POST /v1/ai/analyze`
- req:
```json
{
  "question": "这件事要不要做",
  "method": "digitalManual",
  "primaryNumber": 11,
  "resultingNumber": 26,
  "movingPositions": [1],
  "lines": [7,8,8,7,7,9],
  "hexTextVersion": "yi-zhengshi-2026-08"
}
```
- resp:
```json
{
  "ok": true,
  "analysis": {
    "summary": "先稳后进，宜先收敛再扩张。",
    "focus": "主看初爻，警惕起步阶段节奏过急。",
    "advice": ["先明确边界", "小步试错", "保留回撤空间"]
  },
  "usage": { "promptTokens": 600, "completionTokens": 320 }
}
```

### 6) AI 追问
- `POST /v1/ai/followup`
- Header: `Authorization: Bearer <access_token>`
- req: 与解读相同的卦象字段，外加 `previousAnalysis`、`conversation`、`message`
- resp:
```json
{ "ok": true, "reply": "……", "usage": { "promptTokens": 800, "completionTokens": 200 } }
```

### 7) 案例列表（公开，供 App 热更新）
- `GET /v1/cases`
- 无需登录。Header 可选 `If-None-Match: "<version>"`；未变则 HTTP 304
- resp:
```json
{ "ok": true, "version": "a1b2c3d4e5f60718", "cases": [{ "file": "…", "number": 1, "hexagram": "乾卦", "position": "初爻" }] }
```
- 客户端以服务端列表全量替换本地缓存；离线时用 App 包内 `cases.json`

## 错误码（最小）
- `4001` 参数错误
- `4002` 验证码错误或过期
- `4003` 登录态无效
- `4290` 请求过快（限流）
- `5000` 服务内部错误

## 短信通道（现役）
- `SMS_PROVIDER=mock`：开发可固定码 / 控制台打印；**生产**随机码并写日志（固定 `123456` 默认禁用）
- `SMS_PROVIDER=tencent`：腾讯云短信 SendSms（密钥与签名模板在服务端 `.env`）
- 细节见 `backend/README.md` 与 `backend/app/services/sms.py`

## AI 解读实现说明（现役）
- 服务端拼 prompt：卦辞→事情背景；大象辞→宜努力方向；动爻爻辞/小象→当下情形
- 另附本卦初爻至上爻讲习案例（`cases.json`）作取象参照，不可把案例原事套到用户身上
- 初次 `POST /v1/ai/analyze`；追问 `POST /v1/ai/followup`
- `AI_MODE=mock` 规则解读；`openai` 走 OpenAI 兼容 Chat Completions（密钥在服务端 `.env`）
- 经文取自 `ios/Yizhidao/Resources/Hexagrams.json`（与《易经证释》所引一致）
- 提示词细节以 `backend/app/services/ai.py` 为准

## 运营与安全最小要求
- 按 `userId + ip` 限流
- 分环境密钥（dev/staging/prod）
- 记录 traceId，便于排错
- 日志脱敏手机号、问题文本可选匿名化
- 勿将 `backend/.env`、密钥提交进仓库
