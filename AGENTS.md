# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞；可选后端登录与 AI 解读。

## 怎么跑

```bash
open Yizhidao.xcodeproj
# 或
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

后端本地：

```bash
./start-backend.sh
```

iOS 17+ / Xcode 15+。Bundle：`com.yizhidao.app`。全 App **固定浅色**宣纸主题（`AppTheme` + `preferredColorScheme(.light)`）。

## 技术栈

- **App**：SwiftUI + SwiftData；无第三方依赖。经文在 `Hexagrams.json`（《易经证释》所引）
- **后端**：`backend/` FastAPI + SQLite；短信登录（开发期固定码）与 AI 解读（`AI_MODE=mock|openai`）

## 目录与约定

- `Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `Domain/ReadingGuide`：多动爻主看焦点；`ResultView` 本卦/之卦 tab
- `Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选；删除进回收站
- `App/`：`AppNavigation`、`AppTheme`、登录会话与「我的」Tab（多集中在 `YizhidaoApp.swift`）
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦：可摇 / 「选」手选四象；六爻上→初
- 三数：一键随机；未满三正整数则「起卦」禁用；「清空」始终可点
- 结果页右下角悬浮 **AI**（需登录）；「我的」可从历史进 AI；提示词框架见 `backend/app/services/ai.py`
- Debug API：模拟器 `127.0.0.1:8080`；真机改 `AuthAPI` 中局域网 IP（`ipconfig getifaddr en0`）
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`AppIcon-source.png`、`backend/.env`、`backend/*.db`

## 当前状态 / 下一步

已实现：双法起卦、礼仪、结果 tab＋主看、历史聚合／验证／回收站／同类跳转、浅色主题、「我的」手机号登录、结果页 AI 悬浮解读（后端 mock 或 OpenAI 兼容模型）。分支 `feature/result-reading-tabs`（含未提交的登录/后端/AI 改动）。

未做：微信登录、真实短信通道、`GET /v1/me`、线上部署、App Store、文言讲解层。
