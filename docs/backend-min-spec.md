# 易玩家最小后端接口（登录 + AI + 案例热更新）

本文件是接口合同（路径与字段），以仓库 `backend/app/schemas.py` 为准。App Release 基址：`https://api.yiwanjia.work`（仅海外上架）。`GET /v1/cases` 于 2026-08-17 上线。AI 扩卡（`direction` / `risks` / `askNext`、追问 `advice`）随本分支发版后才到生产；发版前旧客户端仍只依赖三字段解读与单段 `reply`。

## 目标
- 客户端不直连大模型，密钥留在服务端
- 登录与 AI 分析统一走后端，便于限流、审计、计费控制

## 鉴权
- Header: `Authorization: Bearer <access_token>`
- Token 由 Apple / Google / 邮箱验证码登录后签发（短信路由仍保留，现役 App 登录页不展示）

## 接口

### 1) 发送短信验证码（Debug / 遗留）
- `POST /v1/auth/sms/send`
- req:
```json
{ "phone": "13800138000" }
```
- resp:
```json
{ "ok": true, "cooldownSec": 60 }
```

### 2) 手机号验证码登录（Debug / 遗留）
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
  "user": { "id": "u_123", "nickname": "用户138****8000", "phone": "13800138000", "email": null }
}
```

### 3) 当前用户（校验登录态）
- `GET /v1/me`
- Header: `Authorization: Bearer <access_token>`
- resp:
```json
{
  "ok": true,
  "user": { "id": "u_123", "nickname": "用户138****8000", "phone": "13800138000", "email": null }
}
```
- 无效/过期 token → HTTP 401，`code: 4003`

### 3b) 注销账号
- `DELETE /v1/me`
- Header: `Authorization: Bearer <access_token>`
- resp: `{ "ok": true }`
- 删除 `users` 行及该手机号/邮箱下未用验证码；token 随后失效（再 `GET /v1/me` → 401）

### 3c) 法律与支持页（App Store）
- `GET /privacy` · `GET /terms` · `GET /support` → HTML（无鉴权）

### 4) 发送邮箱验证码
- `POST /v1/auth/email/send`
- req:
```json
{ "email": "you@example.com" }
```
- resp:
```json
{ "ok": true, "cooldownSec": 60 }
```

### 5) 邮箱验证码登录
- `POST /v1/auth/email/login`
- req:
```json
{ "email": "you@example.com", "code": "123456" }
```
- resp: 同 §2（`user.email` 有值）

### 6) Sign in with Apple
- `POST /v1/auth/apple`
- req:
```json
{ "identityToken": "<apple_identity_jwt>", "fullName": "可选昵称" }
```
- resp: 同 §2（`user.phone` / `user.email` 可为 null）

### 7) Google 登录
- `POST /v1/auth/google`
- req:
```json
{ "idToken": "<google_id_jwt>" }
```
- resp: 同 §2

### 8) AI 解读
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
    "direction": "宜对照大象，守中而动。",
    "risks": ["起步过急易失节奏"],
    "advice": ["先明确边界", "小步试错"],
    "askNext": ["对方反对的话，我该守还是该转？"]
  },
  "usage": { "promptTokens": 600, "completionTokens": 320 }
}
```
- 旧客户端只读 `summary` / `focus` / `advice`；多出的字段可忽略。现役 App 把 `risks` 并入建议卡，只展示一条。

### 8b) AI 追问
- `POST /v1/ai/followup`
- Header: `Authorization: Bearer <access_token>`
- req: 与解读相同的卦象字段，外加 `previousAnalysis`、`conversation`（可含每轮 `advice`）、`message`
- resp:
```json
{
  "ok": true,
  "reply": "……",
  "advice": ["把新补充收进判断，仍以动爻为主"],
  "askNext": ["我眼下最该先做什么？"],
  "usage": { "promptTokens": 800, "completionTokens": 200 }
}
```
- 旧客户端只读 `reply`

### 9) 案例列表（公开，供 App 热更新）
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
- `4290` 限流（验证码冷却；AI 间隔/并发为「请稍后再试」，当天次数用尽为「今天的解读次数用完了，明天再来」）
- `5000` 服务内部错误

## 邮箱 / OAuth 通道

- `EMAIL_PROVIDER=mock`：终端 `[email:mock]` / `[email:test]`（`print`，非 `logger.info`）；`DEV_EMAIL_FIXED_CODE` 在 development+mock 下对任意合法邮箱生效；白名单 `EMAIL_TEST_ADDRESSES` 在 smtp 下也不发真信
- `EMAIL_PROVIDER=smtp`：通用 SMTP（`SMTP_*`）。现役海外为 Resend：`587` + STARTTLS。App 审核勿配 `EMAIL_TEST_ADDRESSES`
- `APPLE_CLIENT_IDS` / `GOOGLE_CLIENT_IDS`：服务端验 token 的 aud 白名单
- 细节见 `backend/README.md` 与 `backend/app/services/email_otp.py`、`oauth.py`

## 短信通道

- **海外现役 App**（`api.yiwanjia.work`）：Release 登录用 Apple / Google / 邮箱 OTP；短信仅后端路由，App 无入口
- `SMS_PROVIDER=mock`：开发可固定码 / 控制台打印；生产 mock 对普通号随机码写日志（全站固定 `123456` 须 `ALLOW_INSECURE_MOCK_SMS`，勿开）
- `SMS_TEST_PHONES`：逗号分隔白名单；可用 `DEV_SMS_FIXED_CODE`，且不发真实短信（试号 `13800138000`）
- `SMS_PROVIDER=aliyun`：阿里云号码认证（**国内遗留机**曾用；现役海外 App 不用）
- `SMS_PROVIDER=tencent`：腾讯云短信 SendSms（代码保留）
- 细节见 `backend/README.md` 与 `backend/app/services/sms.py`

## AI 解读实现说明

机制（框架、黄庭槽、案例筛选、出卡）见 [`ai-reading.md`](ai-reading.md)。提示词原文以 `backend/app/services/ai.py` 为准。本文件 §8 / §8b 只保留路径与 JSON 字段。

## 运营与安全最小要求
- AI：按登录用户限流（间隔 8 秒、同时 1 个、自然日 UTC+8 合计 40 次含追问）；超限 `4290`。细节见 [`ai-reading.md`](ai-reading.md)
- 发短信/邮箱验证码另有冷却，同用 `4290`
- 分环境密钥（dev/staging/prod）
- 勿将 `backend/.env`、密钥提交进仓库
