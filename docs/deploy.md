# 易知道后端部署指南

目标域名：

- iOS **Release**：`https://yzh.codedance.work`（DNS A → `43.128.104.104`）
- 电脑 / 未中招设备：`https://yizhidao.codedance.work` 仍可用
- 对照：`https://videograb.codedance.work/yzh/health`（同机、同后端）

**现役（2026-08-17 源站核过；2026-08-20 安卓 Cronet 真机登录已通）**：DNS A → `43.128.104.104`。`GET /v1/cases` 313 条（`If-None-Match` 304）；`POST /v1/ai/analyze` 与 `/v1/ai/followup`、`GET /v1/me` 未登录返回 401。与 `videograb.codedance.work` 共用系统 Caddy，API 容器 `docker-compose.prod.yml` 监听 `127.0.0.1:8080`。`yzh` 的 HTTPS **因 TLS 客户端而异**（浏览器 / Cronet 通，Mac curl 与 Java `HttpURLConnection` 常见 RST），不要用「电脑 curl `/health`」代替真机 App 验收。

镜像把 `Hexagrams.json` 拷到 `/app/data/`（与 SQLite 同卷，**重建镜像不会自动刷新经文**）。`cases.json` 默认在镜像 `/app/app/data/`；若 data 卷存在 `/app/data/cases.json` 则优先（热更新不必重建）。可用 `CASES_PATH` 覆盖。

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
{
    servers {
        protocols h1 h2 h3
    }
}

yizhidao.codedance.work, yzh.codedance.work {
    encode gzip
    reverse_proxy 127.0.0.1:8080 {
        transport http {
            read_timeout 180s
            response_header_timeout 180s
        }
    }
}

# 公网 IP 的 HTTP 可进 videograb；不要写 :80 通配，否则抢走 ACME
http://43.128.104.104 {
    encode gzip
    handle /health {
        reverse_proxy 127.0.0.1:8000
    }
    handle {
        reverse_proxy 127.0.0.1:3000 {
            flush_interval -1
        }
    }
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
curl https://yzh.codedance.work/health
# 电脑仍可用：
# curl https://yizhidao.codedance.work/health
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

### 更新案例（App 下次打开「案例」即拉取，不必发版）

优先读 data 卷 `/app/data/cases.json`（有则覆盖镜像内文件）。换文件后**不必重启**：接口按文件 mtime 自动重载。

```bash
# 在服务器 ~/yizhidao/backend
docker compose -f docker-compose.prod.yml cp \
  ../ios/Yizhidao/Resources/cases.json api:/app/data/cases.json
```

或随代码一起重建镜像（卷里若已有 `cases.json` 会继续盖过镜像，要镜像生效就先删卷内该文件）。

## 常见问题

### 证书申请失败

- 确认域名 A 记录已指向本机
- 确认 80 端口未被占用、防火墙已放行
- 等 DNS 生效后再 `docker compose restart caddy`

### App 真机 Release 连不上

- Release 使用 `https://yzh.codedance.work`（iOS `AuthAPI`；安卓 `BuildConfig.API_BASE_URL`）
- Debug 仍走本机 / 局域网 IP；Xcode Scheme 的 Run / Android Studio Build Variant 配成 Release 才会打到生产
- ATS 对 HTTPS 无额外配置需求；明文 `http://` 会被 iOS ATS 拦截（电脑浏览器可以）
- 安卓：浏览器能开 `/health` 仍可能 App Connection reset，见下节 Cronet

### iPhone 11 / HTTP/3（2026-08 踩过）

失败是 **按主机名记住的**，不是整台服务器挂了。同 IP 的 `videograb.codedance.work` 一直能开；电脑和 iPhone 17 上 `yizhidao.codedance.work` 也能开。

1. Caddy 默认开 HTTP/3，并下发 `Alt-Svc: h3; ma=2592000`（约 30 天）。iPhone 11 的 QUIC 比 17 脆，会先表现为 AI/登录超时。
2. **最危险**：已经发过 h3 之后再关掉 UDP 443。手机会继续打 QUIC，Safari「已丢失网络连接」，App `-1200`。客户端收不到一次成功的 HTTP/2，就清不掉缓存。
3. 在已经失败的主机名上换 Let's Encrypt YE/YR 或 ZeroSSL，救不回来，只会把这个 origin 弄得更僵。清 Safari 网站数据、还原网络设置也不一定够。
4. 应急：DNSPod 加**全新子域**（如 `yzh`）A → `43.128.104.104`，让 Caddy 新签证书，App 改 `AuthAPI`。不要用国内真机测 `8.8.8.8`（常被墙）。
5. 验收：改 Caddy/证书后必须用 **iPhone 11 Safari** 打开该域名 `/health`。HTTP/3 要么一直开（与 videograb 相同），要么一开始就永远不开，禁止开关切换。

`videograb` 站点下的 `handle_path /yzh/*` 仍转到本 API，可作对照，不是长期正式域名。

### Android / Cronet（2026-08-20 踩过）

和 iPhone 11 **不是同一类问题**。红米 Note 17 从未连过生产、系统联网权限已开、小米浏览器打开 `https://yzh.codedance.work/health` 能看到 JSON，App 仍可能失败。

1. 默认 Run 是 **Debug**，打 `http://<Mac局域网>:8080`。连生产必须 Build Variant = **release**（`API_BASE_URL` = `https://yzh.codedance.work`）。工程无正式 keystore 时，本机试生产用 debug 签名。
2. 浏览器通 ≠ App 通。小米浏览器走 Chromium（HTTP/2 / HTTP/3）；Java `HttpURLConnection` 打 `yzh` 会在 TLS 握手被 RST（登录页「连不上 …（Connection reset）」）。同机 `videograb.codedance.work/yzh` 对照入口对 curl 是通的。
3. 只补 Let's Encrypt Root YE / X2（`network_security_config`）不够：错误会从笼统「连不上」变成明确的 Connection reset，根因仍是握手被掐。
4. **现役**：App 用 Cronet（`org.chromium.net:cronet-embedded`，开 HTTP/2 + QUIC），与浏览器同栈；红米 Note 17 Release 登录已通。不要改回 `HttpURLConnection` 打生产。
5. 生产短信仍是 mock，**不是**固定 `123456`。验证码：`docker compose -f docker-compose.prod.yml logs -f api | grep -i sms`。

### SQLite 数据在哪

- Docker volume：`yizhidao_data` → 容器内 `/app/data/yizhidao.db`
- 备份：`docker compose exec api ls /app/data`

## 安全清单（上线前勾）

- [x] `JWT_SECRET` 已换成强随机值（生产已配置）
- [x] 未把 `.env` 提交进 Git
- [x] 生产未开启 `ALLOW_INSECURE_MOCK_SMS=true`
- [ ] 有企业后再切 `SMS_PROVIDER=tencent`
- [x] AI Key 仅在服务端