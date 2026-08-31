# 易玩家

原生 SwiftUI iOS + Compose Android：**易玩家**（Bundle / applicationId `com.yizhidao.app`）。数字起卦（三数 / 时间）与六爻金钱卦，记录占时并展示卦象与经文。界面固定浅色宣纸风格。可选 FastAPI 后端：登录与问答。**App Store / Google Play 仅海外**（排除中国大陆）；Release API：`https://api.yiwanjia.work`。

## 要求

- Xcode 15+ / iOS 17+
- macOS 上打开 `ios/Yizhidao.xcodeproj`
- 联调登录 / AI：Python 3.9+（见 `backend/`）

## 打开与运行

```bash
open ios/Yizhidao.xcodeproj
```

选择任意 iPhone Simulator，⌘R 运行。

## 测试

```bash
xcodebuild test -project ios/Yizhidao.xcodeproj -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

## 功能

- **起卦**：页上只有一句话和一个「起卦」按钮。点了走全屏仪式：静心 → 告神（所问必填）→ 选法门（数字起卦 / 时间起卦 / 金钱起卦）→ 取数（三数一个一个落 / 金钱一爻一爻摇 / 时间取选法门那一刻）→ 揭卦。规矩与五条礼仪在「基础入门」
- **数字起卦 · 时间**：只占此刻，按十二时辰取数；没有选时刻，也没有「公历取数」
- **金钱起卦**：摇手机或轻点铜钱，一次一爻，满六自动成卦；长按手选四象；无「一键摇满」。上爻在上、初爻在下
- **结果**：本卦 / 之卦 tab；卦辞、彖曰、象曰、六爻；动爻红字；「主看」；所问默认只读、点编辑才改且不能空；可改验证；**不展示取数**；右上角「同类」；右下角悬浮 **问**（刚起完约 2 秒自动开一次；已有问答则打开不重生成；没有则自动生成且需登录；页标题「问答」，详情亦可点「同类」；一占一条自动保存；卡片为事情背景／当下／方向／建议（须防并入建议），追问后仍给建议；长文由 `AIAnswerFormatter` 展示层分段）；卦辞／彖／大象／爻+小象可点开 **IMA 黄庭书院讲解**（包内 `ImaExplanations.json`）；页底「经文版本：《易经证释》所引」
- **历史**：SwiftData 本地；**时间** / **按卦**（文王序）；状态筛选；左滑删除进回收站；数字起卦单爻动在箭头上方标红字（初/二/三/四/五/上）
- **问答**：列出全部本地问答（左滑删除）；起卦后点「问」会自动出现在这里
- **基础入门**（「我的」）：九章册页，含怎样起卦；章末上一章／下一章并排。英文界面读 `YijingIntro.en.json`（引文仍中文）
- **我的**：登录为 iOS **Apple** / Android **Google**（主按钮）+ 邮箱验证码（子页）；无微信、登录页无短信。资料编辑、**解锁问答**（iOS 本地已接、未发；设置里不放恢复购买）、**基础入门**、**六十四卦**（详情卡片彖辞/大象；经文可点 IMA 讲解；页底证释来源）、**四传**（系辞/说卦/序卦/杂卦；页底证释来源）、**案例**（按卦浏览，打开拉取 `GET /v1/cases`，离线用包内底稿）、**意见反馈**、**检查更新**、设置（回收站，清空需确认；退出登录；**注销账号**）。语言只跟系统：中文简繁，非中文界面壳英文；无应用内开关。无按键音效。

## 协作

- 远端：https://github.com/zhiyi199501-creator/yizhidao
- `main` 受保护：勿直推，经 PR 合并

## 目录

| 路径 | 内容 |
|---|---|
| `ios/` | SwiftUI App（`Yizhidao.xcodeproj`） |
| `android/` | Kotlin 引擎 + Compose App（见 `android/README.md`） |
| `ios/Yizhidao/App/` | 入口、`AppTheme`、`AppNavigation`、登录、「我的」、问答列表 |
| `ios/Yizhidao/Engines/` | 数字 / 金钱起卦、京房卦序、农历／公历时辰 |
| `ios/Yizhidao/Domain/` | 模型与 `ReadingGuide` 解卦焦点 |
| `ios/Yizhidao/Features/` | 起卦 UI、结果（含 AI 追问）、历史（含按卦）、案例（入口在「我的」）、`Me/ProfileEditView`（资料：头像／昵称／绑邮箱，本地未发）、`Reading/UnlockReadingsView`（iOS 买断页，未发） |
| `ios/Yizhidao/Data/` | SwiftData `ReadingRecord`、回收站、`SavedAIAnalysis`、经文加载、`ImaExplanationStore`、`UnlockStore`（iOS 买断问答，未发） |
| `ios/Yizhidao/Resources/Hexagrams.json` | 64 卦（卦辞／彖／象／爻／用九用六／文言）+ 系辞／说卦／序卦／杂卦。编辑根目录 `易经正文编辑表.xlsx` → `python3 scripts/import_jingwen.py` |
| `ios/Yizhidao/Resources/Zhengshi.json` | 《易经证释》全册阅读稿；由 `scripts/import_zhengshi.py` 从全册 `.doc` 生成，暂未挂「我的」入口 |
| `ios/Yizhidao/Resources/YijingIntro.json` | 基础入门九章（含怎样起卦）。英文界面另读 `YijingIntro.en.json`。Android `copyIosAssets` 拷这两份 |
| `ios/Yizhidao/Resources/cases.json` | 讲习案例包内底稿。日常在 `admin/`「案例」编辑并发布（立刻热更新 `GET /v1/cases`）；导出 JSON 再提交本文件。生产镜像未含后台前仍 `docker compose cp`，见 `docs/deploy.md` |
| `ios/Yizhidao/Resources/ImaExplanations.json` | IMA 黄庭书院讲解（原稿已去出处后标）。后台「黄庭」改 `answer` 立刻影响服务端 AI，App 弹层要发版。`scripts/export_ima_explanations.py` 会覆盖手改 |
| `admin/` | 内部运营后台（用量 + 内容，白底）。本地 `npm run dev` 或 build 后走 `8080/admin/`；生产须重建镜像。不上架 |
| `ios/YizhidaoTests/` | 起卦、时辰、`ReadingGuide`、基础入门、`ImaAnswerFormatter`、`AIAnswerFormatter` 单测 |
| `docs/backend-min-spec.md` | 登录、AI、IAP 验单、案例热更新的接口合同 |
| `docs/ai-reading.md` | AI 问答机制（prompt、黄庭槽、案例筛选、出卡） |
| `docs/deploy.md` | 后端 Docker + Caddy 上线指南 |
| `backend/` | FastAPI：Apple / Google / 邮箱 OTP + AI 问答 |
| `start-backend.sh` | 一键启动后端（本地开发） |

## 后端本地联调

```bash
./start-backend.sh
```

- iOS **Debug**：模拟器 → `http://127.0.0.1:8080`；真机 → Mac 局域网 IP（代码里 `AuthAPI`，查 IP：`ipconfig getifaddr en0`）
- iOS **Release**：`https://api.yiwanjia.work`（Connect **排除中国大陆**）。Scheme → Build Configuration 选 Release。
- Android **Debug**：`android/app/build.gradle.kts` 局域网 IP；**Release**：Build Variant = release，Cronet → 同上生产域名。浏览器能开 `/health` 不算过。
- 本地邮箱：`EMAIL_PROVIDER=mock`；`DEV_EMAIL_FIXED_CODE` 有值则任意合法邮箱用该码，为空则看终端 `[email:mock]`。生产走 SMTP（Resend），审核勿配邮箱白名单。TLS / HTTP/3 / 安卓 Cronet 见 `docs/deploy.md`
- AI：`backend/.env` 中 `AI_MODE=mock`（规则）或 `openai`（OpenAI **兼容协议**，本机常用 DeepSeek）。机制见 `docs/ai-reading.md`，App 接口见 `docs/backend-min-spec.md`
- 运营后台（本地）：`ADMIN_PASSWORD` + `cd admin && npm run dev` → `http://127.0.0.1:5173/admin/`，或 `npm run build` 后开 `http://127.0.0.1:8080/admin/`。生产 `/admin/` 2026-08-31 仍 404（PR #15 已合 `origin/main`），见 `docs/deploy.md`
