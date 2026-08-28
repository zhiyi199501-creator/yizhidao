# 易玩家后端部署指南

## 域名与服务器（2026-08-27）

| 角色 | 域名 | 服务器 | 说明 |
|---|---|---|---|
| **App Release（现役）** | `https://api.yiwanjia.work` | `124.156.192.137`（SSH `yiwanjia`） | 仅海外上架；H2-only |
| **同机备用名** | `https://v1.yiwanjia.work` | 同上 | 仍指向同一 API；App 不再使用 |
| **国内后端（遗留，App 不用）** | `https://yzd.codedance.work` | `119.91.239.58`（SSH `yizhidao`） | 运维保留；现役 App 不连 |
| **旧海外机（遗留）** | `yd.codedance.work` 等 | `43.128.104.104` | 与 videograb 共用系统 Caddy；2026-08-26 改为 **H2-only**（实测关代理不通、开代理通） |

**现役核验（2026-08-28）**：`GET https://api.yiwanjia.work/health` 200；`/privacy` `/terms` `/support` 200；响应 **HTTP/2**，无 `alt-svc`。新加坡机跑 `docker compose`（`backend-caddy-1` + `backend-api-1`），**不是** `docker-compose.prod.yml`。AI 扩卡已合 `main`（PR #12），镜像未重建，解读仍是三字段。

### DNS（`yiwanjia.work`）

2026-08-26 核验：注册商 **DNSPod**（腾讯，WHOIS Registrant Country CN）；权威 NS `ivy.dnspod.net` / `justin.dnspod.net`；`api` / `v1` 的 A 记录是源站 `124.156.192.137`。

- **禁止橙云 / CDN 代理**生产 API。橙云让客户端和 Cloudflare 握手（默认 h3 + `Alt-Svc`），源站 H2-only 失效；国内 CF 段干扰更重；App JSON 还可能碰到挑战页或超时。
- 若迁 NS 到 Cloudflare，只用 **DNS only（灰云）**，A 仍填源站 IP。只换 NS 不改变国内 443 干扰，也换不了 DNSPod 作为注册商的管辖。

镜像把 `Hexagrams.json` 拷到 `/app/data/`（与 SQLite 同卷，**重建镜像不会自动刷新经文**）。`cases.json` 与 `ImaExplanations.json` 默认在镜像 `/app/app/data/`；若 data 卷存在同名文件则优先。可用 `CASES_PATH` / `IMA_EXPLANATIONS_PATH` 覆盖。

## 你需要准备什么

1. 海外 VPS + 独立域名（不备案）；安全组放行 **80 / 443**
2. 域名 **A 记录** → 服务器公网 IP（DNS-only；**不要** Cloudflare 橙云 / CDN）
3. Docker + Docker Compose

> **现役 App（海外机）登录**（2026-08-24）：Release 用 Apple / Google / 邮箱 OTP；生产 `EMAIL_PROVIDER=smtp`（Resend），`EMAIL_TEST_ADDRESSES` 与 `DEV_EMAIL_FIXED_CODE` 应空。短信路由仍在，App 登录页不展示。  
> **国内遗留机**仍可能是 `SMS_PROVIDER=aliyun`；与现役 App 无关。

## 海外新加坡机（现役）

```bash
ssh yiwanjia
cd ~/yizhidao/backend
# 仓库根 Caddyfile 是国内 yzd；新加坡必须用 overseas，compose 挂的是 ./Caddyfile
sudo cp Caddyfile.overseas Caddyfile
sudo docker compose up -d --build
curl -s https://api.yiwanjia.work/health
# FastAPI 对 HEAD /health 返回 405，要用 GET 看协议头
curl -sD - -o /dev/null https://api.yiwanjia.work/health | grep -iE 'HTTP/|alt-svc'   # 期望 HTTP/2，无 alt-svc
```

从本机同步（**不要 `--delete`**，会清服务器 `.env`）：

```bash
# 源是仓库根：排除 backend/.env
rsync -az --exclude '.git' --exclude '.derivedData' --exclude 'backend/.env' \
  --exclude 'backend/.venv' --exclude 'backend/*.db' \
  -e "ssh -i ~/.ssh/yiwanjia.pem" \
  ./ ubuntu@124.156.192.137:~/yizhidao/
# 源若是 backend/ 目录，必须 --exclude '.env'（写 backend/.env 挡不住，会覆盖生产配置）
# rsync 会把国内用的 Caddyfile 盖过来，必须再拷 overseas 后起默认 compose
ssh yiwanjia 'cd ~/yizhidao/backend && sudo cp Caddyfile.overseas Caddyfile && sudo docker compose up -d --build'
```

现役新加坡是 **`docker compose`（api + Caddy 同项目，80/443 仅 tcp）**。`docker-compose.prod.yml` 只给与**系统 Caddy** 共用端口的遗留机用；在新加坡上切过去会丢掉 443。改 `.env` 后须 `--force-recreate`，否则容器仍是旧环境变量。

## 国内新服务器（遗留，方式 A）

`backend/Caddyfile` 仍配 H2-only + `yzd.codedance.work`。**现役 App 不连此机**；仅运维对照或日后另做国内产品时使用。

```bash
cd backend
cp .env.example .env
docker compose up -d --build
```

检查（遗留）：

```bash
curl -s https://yzd.codedance.work/health
```

从本机同步遗留机：`ssh yizhidao` + 同上 rsync 到国内路径。

### 安卓侧载 APK（遗留）

国内机 Caddy 曾挂 `/download/`；**现役海外上架不依赖侧载**。若仍要挂包，勿写进 App Release 基址。

## 旧海外 codedance 服务器（方式 B，遗留）

`43.128.104.104` 与 videograb 共用系统 Caddy；API 仅 `docker-compose.prod.yml` 监听 `127.0.0.1:8080`。易知道 API 已迁出（先国内 `yzd`，现役 App 在新加坡 `api.yiwanjia.work`）；此节仅供 videograb / 旧域名对照。

```bash
cd ~/yizhidao/backend
docker compose -f docker-compose.prod.yml up -d --build
curl https://yd.codedance.work/health   # 2026-08-26 起 H2-only；遗留对照，勿给 App 当 Release
```

公网 IP 的 HTTP 进 videograb 用 `http://43.128.104.104`；**不要**写 `:80` 通配，否则抢走 ACME。

## 更新版本

**海外新加坡机（现役）**：rsync 到 `yiwanjia` 后 `sudo cp Caddyfile.overseas Caddyfile && sudo docker compose up -d --build`（改 env 加 `--force-recreate`）。

**国内遗留机**：`ssh yizhidao` + 同上（仅运维对照）。

**更新案例**（App 下次打开「案例」即拉取；在对应机 `backend/` 下执行）：

```bash
docker compose cp ../ios/Yizhidao/Resources/cases.json api:/app/data/cases.json
```

## 常见问题

### 证书申请失败

- 域名 A 记录已指向本机（不要橙云）；80/443 已放行；等 DNS 生效后 `docker compose restart caddy`

### App 真机 Release 连不上

- Release 基址：`https://api.yiwanjia.work`（Connect 排除中国大陆）
- 国内 iPhone 11 蜂窝直连失败时先开代理，不要换子域/换证
- Debug 走局域网 IP；Xcode / Android Studio 须 **Release** 变体
- 安卓须 **Cronet**，勿用 `HttpURLConnection`；**不要** `addQuicHint`

### iPhone 11 / 国内直连

**2026-08-26 纠正**：失败主要是国内蜂窝直连该 IP 的 443（开代理即通），不是「关 HTTP/3 后主机名永久报废」。`api.yiwanjia.work` 开代理可通；`yd` 关掉 h3 后关代理不通、开代理仍通。不要用轮换子域救场。

现役 `api.yiwanjia.work` H2-only。国内 iPhone 11 验收用代理（TUN 全局，API 域名走代理）。海外用户直连即可。

### Android / Cronet

浏览器通 ≠ App 通。Release 用 Cronet + HTTP/2；勿加 QuicHint 指生产域。详见 `android/README.md`。

### SQLite 数据在哪

- Docker volume：`backend_yizhidao_data` → 容器 `/app/data/yizhidao.db`

## 安全清单

- [x] 海外机 `JWT_SECRET` 已换成强随机值（与国内遗留机分开）
- [x] 未把 `.env` 提交进 Git
- [x] 海外现役 `SMS_PROVIDER=mock`（App 无短信入口；勿开 `ALLOW_INSECURE_MOCK_SMS`）
- [x] `https://api.yiwanjia.work/{privacy,terms,support}` 可访问（Connect 法律 URL）
- [x] App **不上中国区** → 现役路径 **无需 ICP**；基址保持 `api.yiwanjia.work`
- [x] Apple / 邮箱登录（客户端 + 后端）；生产邮箱 SMTP（Resend，2026-08-24 已 live：`email_provider=smtp`）
- [ ] Android Google：生产 `GOOGLE_CLIENT_IDS` 与 `GOOGLE_WEB_CLIENT_ID`
- [ ] 正式固化新加坡机镜像（避免只热更代码）
- [ ] IAP 验单
