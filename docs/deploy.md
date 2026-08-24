# 易知道后端部署指南

## 域名与服务器（2026-08-22）

| 角色 | 域名 | 服务器 | 说明 |
|---|---|---|---|
| **App Release（现役）** | `https://api.yiwanjia.work` | `124.156.192.137`（SSH `yiwanjia`） | 仅海外上架；Docker + `Caddyfile.overseas` |
| **国内后端（遗留，App 不用）** | `https://yzd.codedance.work` | `119.91.239.58`（SSH `yizhidao`） | 运维保留；现役 App 不连 |
| **旧海外机（遗留）** | `yd.codedance.work` 等 | `43.128.104.104` | 与 videograb 共用系统 Caddy，**仍开 HTTP/3**；勿改 App 指回 |
| **iPhone 11 已废** | `yizhidao.codedance.work` / `yzh.codedance.work` | — | 勿换证、勿关 h3 救场 |

**现役核验**：`GET https://api.yiwanjia.work/health` 200；`/v1/cases` 313 条；响应 **HTTP/2**。

镜像把 `Hexagrams.json` 拷到 `/app/data/`（与 SQLite 同卷，**重建镜像不会自动刷新经文**）。`cases.json` 默认在镜像 `/app/app/data/`；若 data 卷存在 `/app/data/cases.json` 则优先（热更新不必重建）。可用 `CASES_PATH` 覆盖。

## 你需要准备什么

1. 云服务器（国内机需 **ICP 备案** 后才能长期用域名提供 HTTPS）
2. 域名 **A 记录** → 服务器公网 IP；安全组放行 **80 / 443**
3. Docker + Docker Compose
4. 国内拉镜像慢：配置 Docker registry mirror（如 `https://mirror.ccs.tencentyun.com`）；build 失败时可从旧机 `docker save` 导入 `backend-api` 镜像

> **现役短信**：`SMS_PROVIDER=aliyun`（阿里云号码认证）。白名单 `SMS_TEST_PHONES`（现役 `13800138000` / `123456`）仍固定码、不发真短信。勿开 `ALLOW_INSECURE_MOCK_SMS`。企业资质后再考虑 `tencent`。

## 国内新服务器（方式 A，现役）

`backend/Caddyfile` 已配 H2-only + `yzd.codedance.work`。备案通过后复制同结构块，把域名改成 `yizhidao.work`。

```bash
cd backend
cp .env.example .env   # 生产从旧机复制 .env，勿提交 Git
docker compose up -d --build   # 国内 build 超时可 docker save/load 镜像
```

`.env` 生产最小建议：

```bash
APP_ENV=production
JWT_SECRET=请换成很长的随机字符串
SMS_PROVIDER=aliyun
DEV_SMS_FIXED_CODE=123456
SMS_TEST_PHONES=13800138000
ALLOW_INSECURE_MOCK_SMS=false
ALIYUN_ACCESS_KEY_ID=...
ALIYUN_ACCESS_KEY_SECRET=...
ALIYUN_SMS_SIGN_NAME=恒创联众
ALIYUN_SMS_TEMPLATE_CODE=100001
ALIYUN_SMS_TEMPLATE_PARAM={"code":"##code##","min":"5"}
AI_MODE=openai
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-chat
```

检查：

```bash
curl https://yzd.codedance.work/health
curl -sI https://yzd.codedance.work/health | grep -iE 'HTTP/|alt-svc'
curl -sI https://yzd.codedance.work/privacy | head -1   # App Store 隐私政策 URL
curl -sI https://yzd.codedance.work/support | head -1   # Support URL
docker compose logs -f api
```

> 改 `.env` 后须 `docker compose up -d` **重建容器**才会加载新环境变量；仅 `restart` 不够。法律页与 aliyun SDK 已热更新过；正式固化请 `docker compose up -d --build`（国内 pip 可能慢）。

从本机同步（**不要 `--delete`**，会清服务器 `.env`）：

```bash
rsync -az --exclude '.git' --exclude '.derivedData' --exclude 'backend/.env' \
  --exclude 'backend/.venv' --exclude 'backend/*.db' \
  ./ yizhidao:~/yizhidao/
ssh yizhidao 'cd ~/yizhidao/backend && sudo docker compose up -d --build'
```

### 安卓侧载 APK

Caddy `handle /download/*` → `/var/www/yizhidao/download/`（容器内路径需挂卷或拷入主机）。现役 APK 文件名 `yizhidao-0.1.1.apk`；域名随 Release 基址（备案后改为 `yizhidao.work`）。

## 旧海外 codedance 服务器（方式 B，遗留）

`43.128.104.104` 与 videograb 共用系统 Caddy；API 仅 `docker-compose.prod.yml` 监听 `127.0.0.1:8080`。易知道已迁至国内新服务器；此节仅供 videograb / 旧域名对照。

```bash
cd ~/yizhidao/backend
docker compose -f docker-compose.prod.yml up -d --build
curl https://yd.codedance.work/health   # 仍开 h3，勿给 iPhone 11 当正式 Release
```

公网 IP 的 HTTP 进 videograb 用 `http://43.128.104.104`；**不要**写 `:80` 通配，否则抢走 ACME。

## 更新版本

**国内新服务器（方式 A）**：rsync 后 `docker compose up -d --build`（或 `--no-build` 若只改 Caddyfile）。

**更新案例**（App 下次打开「案例」即拉取）：

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

- [x] `JWT_SECRET` 已换成强随机值（生产已配置）
- [x] 未把 `.env` 提交进 Git
- [x] 生产未开启 `ALLOW_INSECURE_MOCK_SMS=true`
- [x] 生产短信 `SMS_PROVIDER=aliyun`（号码认证）
- [x] `/privacy` `/terms` `/support` 可访问（App Store 用）
- [ ] `yizhidao.work` ICP 备案完成并改 App 基址
- [ ] 有企业后再视需要切 `SMS_PROVIDER=tencent`
- [ ] 正式 `docker compose up -d --build` 固化热更新进镜像
