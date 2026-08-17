# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞；可选后端登录与 AI 解读。

## 怎么跑

```bash
open Yizhidao.xcodeproj
# 或
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

安卓引擎单测（只需 JDK 17）：

```bash
cd android && ./gradlew :engines:test
```

用 Android Studio 打开 `android/` 跑 App。后端本地：`./start-backend.sh`。iOS 17+ / Xcode 15+。Bundle / applicationId：`com.yizhidao.app`。全 App **固定浅色**宣纸主题。

## 技术栈

- **App（iOS）**：SwiftUI + SwiftData；无第三方依赖。经文 `Hexagrams.json`（《易经证释》所引）；案例 `cases.json`
- **App（Android）**：Kotlin + Jetpack Compose；引擎在 `android/engines`（纯 JVM，与 iOS 单测对拍）。见 `android/README.md`
- **后端**：`backend/` FastAPI + SQLite；短信登录（开发期固定码）与 AI（`AI_MODE=mock|openai`）

## 目录与约定

- Tab 顺序：起卦 / 历史 / 案例 / 我的
- `Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `Domain/ReadingGuide`：多动爻主看焦点；本卦/之卦展示在 `HexagramReadingBody`（结果页与案例详情共用）
- `Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选；删除进回收站
- `Features/Cases/`：按卦列表（无时间、无应验徽章）；本分支 iOS 打开时 `GET /v1/cases` 拉最新，失败则用包内/缓存；详情为背景 / 所问 / 验证 / 讲师解读 + 本卦之卦
- 案例底稿：`Yizhidao/Resources/cases.json`。编辑根目录 `案例编辑表.xlsx`（gitignore）→ `python3 scripts/import_cases.py`；导出 `python3 scripts/export_cases.py`。补占编号按实际文王序，括号标讲座来源（如 `01-3乾卦三爻（从大有卦三爻讲）`）
- `App/`：`AppNavigation`、`AppTheme`、登录与「我的」（多在 `YizhidaoApp.swift`）。「我的」：资料、**AI解读历史**（本地保存的解读）、回收站（清空需确认）、设置（退出登录）
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦：可摇 / 「选」手选四象；六爻上→初
- 三数：一键随机；未满三正整数则「起卦」禁用；「清空」始终可点
- 结果页悬浮 **AI**（需登录）：初次结构化解读，可追问/补背景；合适则 **保存** 到「我的 → AI解读历史」。提示词见 `backend/app/services/ai.py`（含本卦初–上案例作参照）
- Debug API：模拟器 `127.0.0.1:8080`；真机 Debug 改 `AuthAPI` 局域网 IP；**Release** → `https://yizhidao.codedance.work`
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`.workbuddy/`、`AppIcon-source.png`、`backend/.env`、`backend/*.db`、`案例编辑表.xlsx`、`张庆祥讲易经案例_txt/`、`Yizhidao.xcscheme` 里把 Run 改成 Release 的本机偏好

## 当前状态 / 下一步

**main / 生产（PR #3，2026-08-16 核过）**：双法起卦、礼仪、主看、历史、案例按卦（包内底稿）、登录、`GET /v1/me`、AI 解读＋追问＋本地保存。`https://yizhidao.codedance.work` 有 `analyze` / `followup` / `me`。**无** `GET /v1/cases`。

**本分支 `feature/cases-live-update`**（远程已有同名分支，**未开 PR、未合入 main**）：后端 `GET /v1/cases` + iOS 热更新。2026-08-17 对生产该路径 live probe 连接被重置，未拿到 HTTP 状态；以 main 无此路由为准，**尚未部署**。本地 `cases.json` 313 条（谦卦 0），与编辑表已对齐，内容未提交。

未做：微信登录、生产短信、App Store、文言讲解层。运维见 `docs/deploy.md`。

**Android（进行中，工作区未提交）**：引擎对拍 iOS 单测；Compose 四 Tab 可用；登录、AI、回收站、案例热更新未接。见 `android/README.md`。
