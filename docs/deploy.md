# 易知道后端部署指南

目标域名（App Release 已写死）：`https://yizhidao.codedance.work`

**现役（2026-08-16 已验证）**：DNS A → `43.128.104.104`；HTTPS `/health` 正常；`POST /v1/ai/analyze` 与 `/v1/ai/followup`、`GET /v1/me` 未登录返回 401。与 `videograb.codedance.work` 共用系统 Caddy，API 容器 `docker-compose.prod.yml` 监听 `127.0.0.1:8080`。

镜像把 `Hexagrams.json` 拷到 `/app/data/`（与 SQLite 同卷，**重建镜像不会自动刷新经文**）；`cases.json` 在 `/app/app/data/`，跟镜像走、不被 data 卷盖掉。

## 你需要准备什么

1. 一台云服务器（腾讯云轻量 / 阿里云 ECS 均可，2核2G 足够起步）
2. 域名 `yizhidao.codedance.work`（或你实际要用的子域）**A 记录**解析到服务器公网 IP
3. 服务器已安装 Docker + Docker Compose
4. 服务器放行 **80 / 443** 端口（Caddy 自动签 HTTPS 证书）

> 没有企业资质时，短信继续用 mock：**生产环境默认不会使用固定** `123456`，验证码会随机生成并打印在容器日志里，仅供你自己测试。公网任何人都能「发码」，请尽快换成真实短信或加额外防护。

## 一键部署（推荐）

在本地或服务器上，进入仓库后：

```bash
cd backend
cp .env.example .env
# 编辑 .env：至少改 JWT_SECRET、AI 相关；域名见下
```

`.env` 生产最小建议：

```bash
APP_ENV=production
JWT_SECRET=请换成很长的随机字符串
SMS_PROVIDER=mock
DEV_SMS_FIXED_CODE=
ALLOW_INSECURE_MOCK_SMS=false

# AI（可选）
AI_MODE=openai
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-chat
```

### 方式 A：独占服务器（Docker 内置 Caddy）

```bash
cd backend
docker compose up -d --build
```

### 方式 B：与已有系统 Caddy 共用 80/443（当前 codedance 服务器）

1. 在 `/etc/caddy/Caddyfile` 增加：

```
yizhidao.codedance.work {
    encode gzip
    reverse_proxy 127.0.0.1:8080
}
```

2. `sudo systemctl reload caddy`

3. 只起 API 容器（绑定本机 8080）：

```bash
cd backend
docker compose -f docker-compose.prod.yml up -d --build
```

检查：

```bash
curl https://yizhidao.codedance.work/health
# 或（方式 B 生产）
docker compose -f docker-compose.prod.yml logs -f api
```

mock 验证码在日志里：

```bash
docker compose -f docker-compose.prod.yml logs -f api | grep -i sms
```

## 更新版本

**方式 A（独占 Caddy）**：

```bash
cd /path/to/yizhidao
git pull   # 或 rsync 同步
cd backend
docker compose up -d --build
```

**方式 B（当前 codedance 服务器）**：

从本机同步到 `ubuntu@43.128.104.104:~/yizhidao/`。**不要加 `--delete`**：会清掉服务器上仓库里没有的文件（含 `.env`、运行产物）。排除 `.git`、`.derivedData`、`backend/.env`、`.venv`、转录与案例原稿目录。然后：

```bash
cd ~/yizhidao/backend
docker compose -f docker-compose.prod.yml up -d --build
```

经文若有更新，还需拷进 data 卷（`/app/data/Hexagrams.json`），或改 `HEXAGRAMS_PATH` 指到卷外路径。

## 常见问题

### 证书申请失败

- 确认域名 A 记录已指向本机
- 确认 80 端口未被占用、防火墙已放行
- 等 DNS 生效后再 `docker compose restart caddy`

### App 真机 Release 连不上

- Release 使用 `https://yizhidao.codedance.work`（见 `AuthAPI`）
- Debug 仍走本机 / 局域网 IP
- ATS 对 HTTPS 无额外配置需求

### SQLite 数据在哪

- Docker volume：`yizhidao_data` → 容器内 `/app/data/yizhidao.db`
- 备份：`docker compose exec api ls /app/data`

## 安全清单（上线前勾）

- [x] `JWT_SECRET` 已换成强随机值（生产已配置）
- [x] 未把 `.env` 提交进 Git
- [x] 生产未开启 `ALLOW_INSECURE_MOCK_SMS=true`
- [ ] 有企业后再切 `SMS_PROVIDER=tencent`
- [x] AI Key 仅在服务端