# 易玩家后端部署指南

## 域名与服务器（2026-08-24）

| 角色 | 域名 | 服务器 | 说明 |
|---|---|---|---|
| **App Release（现役）** | `https://api.yiwanjia.work` | `124.156.192.137`（SSH `yiwanjia`） | 仅海外上架；Docker + `Caddyfile.overseas`；**H2-only** |
| **国内后端（遗留，App 不用）** | `https://yzd.codedance.work` | `119.91.239.58`（SSH `yizhidao`） | 运维保留；现役 App 不连 |
| **旧海外机（遗留）** | `yd.codedance.work` 等 | `43.128.104.104` | 与 videograb 共用系统 Caddy，**仍开 HTTP/3**；勿改 App 指回 |
| **iPhone 11 已废** | `yizhidao.codedance.work` / `yzh.codedance.work` | — | 勿换证、勿关 h3 救场 |

**现役核验（2026-08-24）**：`GET https://api.yiwanjia.work/health` 200；`/privacy` `/terms` `/support` 200；响应 **HTTP/2**（经 Cloudflare 时 `via: Caddy` 仍可见）。

镜像把 `Hexagrams.json` 拷到 `/app/data/`（与 SQLite 同卷，**重建镜像不会自动刷新经文**）。`cases.json` 默认在镜像 `/app/app/data/`；若 data 卷存在 `/app/data/cases.json` 则优先（热更新不必重建）。可用 `CASES_PATH` 覆盖。

## 你需要准备什么

1. 海外 VPS + 独立域名（不备案）；安全组放行 **80 / 443**
2. 域名 **A 记录** → 服务器公网 IP（Cloudflare 可用橙云；SSL 建议 Full strict）
3. Docker + Docker Compose

> **现役 App（海外机）短信**：`SMS_PROVIDER=mock`；白名单 `SMS_TEST_PHONES=13800138000` / `123456`。正式海外登录待 Apple / Google OAuth，不走国际短信。  
> **国内遗留机**仍可能是 `SMS_PROVIDER=aliyun`；与现役 App 无关。

## 海外新加坡机（现役）

```bash
ssh yiwanjia
cd ~/yizhidao/backend
# Caddyfile 用海外模板（仓库 backend/Caddyfile.overseas → 线上 Caddyfile）
cp .env.example .env   # 生产勿用示例密钥；JWT 与国内机分开
docker compose up -d --build
curl -s https://api.yiwanjia.work/health
curl -sI https://api.yiwanjia.work/health | grep -iE 'HTTP/|alt-svc'   # 期望 HTTP/2，无 alt-svc:h3
```

从本机同步（**不要 `--delete`**，会清服务器 `.env`）：

```bash
rsync -az --exclude '.git' --exclude '.derivedData' --exclude 'backend/.env' \
  --exclude 'backend/.venv' --exclude 'backend/*.db' \
  -e "ssh -i ~/.ssh/yiwanjia.pem" \
  ./ ubuntu@124.156.192.137:~/yizhidao/
ssh yiwanjia 'cd ~/yizhidao/backend && sudo docker compose up -d --build'
```

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
curl https://yd.codedance.work/health   # 仍开 h3，勿给 iPhone 11 当正式 Release
```

公网 IP 的 HTTP 进 videograb 用 `http://43.128.104.104`；**不要**写 `:80` 通配，否则抢走 ACME。

## 更新版本

**海外新加坡机（现役）**：rsync 到 `yiwanjia` 后 `docker compose up -d --build`。

**国内遗留机**：`ssh yizhidao` + 同上（仅运维对照）。

**更新案例**（App 下次打开「案例」即拉取；在对应机 `backend/` 下执行）：

```bash
docker compose cp ../ios/Yizhidao/Resources/cases.json api:/app/data/cases.json
```

## 常见问题

### 证书申请失败

- 域名 A 记录已指向本机；80/443 已放行；等 DNS 生效后 `docker compose restart caddy`

### App 真机 Release 连不上

- Release 基址：`https://api.yiwanjia.work`（Connect 排除中国大陆）
- Debug 走局域网 IP；Xcode / Android Studio 须 **Release** 变体
- 安卓须 **Cronet**，勿用 `HttpURLConnection`；**不要** `addQuicHint`

### iPhone 11 / HTTP/3

失败按**主机名**记住。旧海外机 `yd` 仍发 `Alt-Svc: h3`——**不要**对其关 UDP 443。

**国内新服务器**：从第一天 `protocols h1 h2`（或新域名不广告 h3）。新主机名无 Alt-Svc 缓存，Safari / URLSession 走 TCP/H2。

已废主机名：`yizhidao` / `yzh`（部分 iPhone 11）；不要换 CA 救场。应急用**全新**子域或迁新服务器。

验收：改 Caddy/证书后 **iPhone 11 Safari** 开 `/health`，确认 HTTP/2、无 `alt-svc`，再装 Release。

### Android / Cronet

浏览器通 ≠ App 通。Release 用 Cronet + HTTP/2；勿加 QuicHint 指生产域。详见 `android/README.md`。

### SQLite 数据在哪

- Docker volume：`backend_yizhidao_data` → 容器 `/app/data/yizhidao.db`

## 安全清单

- [x] 海外机 `JWT_SECRET` 已换成强随机值（与国内遗留机分开）
- [x] 未把 `.env` 提交进 Git
- [x] 海外现役 `SMS_PROVIDER=mock` + 白名单试号（正式登录待 OAuth；勿开 `ALLOW_INSECURE_MOCK_SMS` 给公网任意号）
- [x] `https://api.yiwanjia.work/{privacy,terms,support}` 可访问（Connect 法律 URL）
- [x] App **不上中国区** → 现役路径 **无需 ICP** / 无需改 `yizhidao.work` App 基址
- [ ] 正式 `docker compose up -d --build` 固化新加坡机镜像（若曾热更新）
- [ ] Apple / Google 登录与 IAP 验单
