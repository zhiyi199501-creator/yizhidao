# 易知道最小后端接口草案（登录 + AI分析）

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

### 3) 微信登录（code 换会话）
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

### 4) AI 解读
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

## 错误码（最小）
- `4001` 参数错误
- `4002` 验证码错误或过期
- `4003` 登录态无效
- `4290` 请求过快（限流）
- `5000` 服务内部错误

## AI 解读实现说明（现役）
- 服务端拼 prompt：卦辞→事情背景；大象辞→宜努力方向；动爻爻辞/小象→当下情形
- `AI_MODE=mock` 规则解读；`openai` 走 OpenAI 兼容 Chat Completions（密钥在服务端 `.env`）
- 经文取自 App 仓库 `Yizhidao/Resources/Hexagrams.json`（与《易经证释》所引一致）
- 提示词细节以 `backend/app/services/ai.py` 为准

## 运营与安全最小要求
- 按 `userId + ip` 限流
- 分环境密钥（dev/staging/prod）
- 记录 traceId，便于排错
- 日志脱敏手机号、问题文本可选匿名化
- 勿将 `backend/.env`、密钥提交进仓库
