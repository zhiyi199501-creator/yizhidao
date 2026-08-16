# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞；可选后端登录与 AI 解读。

## 怎么跑

```bash
open Yizhidao.xcodeproj
# 或
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

后端本地：`./start-backend.sh`。iOS 17+ / Xcode 15+。Bundle：`com.yizhidao.app`。全 App **固定浅色**宣纸主题。

## 技术栈

- **App**：SwiftUI + SwiftData；无第三方依赖。经文 `Hexagrams.json`（《易经证释》所引）；案例 `cases.json`
- **后端**：`backend/` FastAPI + SQLite；短信登录（开发期固定码）与 AI（`AI_MODE=mock|openai`）

## 目录与约定

- Tab 顺序：起卦 / 历史 / 案例 / 我的
- `Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `Domain/ReadingGuide`：多动爻主看焦点；本卦/之卦展示在 `HexagramReadingBody`（结果页与案例详情共用）
- `Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选；删除进回收站
- `Features/Cases/`：按卦列表（无时间、无应验徽章）；卦内不分数字/金钱；详情为背景 / 所问 / 验证 / 讲师解读 + 本卦之卦
- `App/`：`AppNavigation`、`AppTheme`、登录与「我的」（多在 `YizhidaoApp.swift`）。「我的」：资料、**AI解读历史**（本地保存的解读）、回收站（清空需确认）、设置（退出登录）
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦：可摇 / 「选」手选四象；六爻上→初
- 三数：一键随机；未满三正整数则「起卦」禁用；「清空」始终可点
- 结果页悬浮 **AI**（需登录）：初次结构化解读，可追问/补背景；合适则 **保存** 到「我的 → AI解读历史」。提示词见 `backend/app/services/ai.py`（含本卦初–上案例作参照）
- Debug API：模拟器 `127.0.0.1:8080`；真机 Debug 改 `AuthAPI` 局域网 IP；**Release** → `https://yizhidao.codedance.work`
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`.workbuddy/`、`AppIcon-source.png`、`backend/.env`、`backend/*.db`

## 当前状态 / 下一步

已实现：双法起卦、礼仪、主看、历史聚合／验证／回收站、案例按卦、浅色主题、手机号登录、`GET /v1/me`、AI 解读＋追问＋本地保存。短信默认 mock（腾讯云代码已备，需企业资质）。**后端已部署**（2026-08-16 核过）：`https://yizhidao.codedance.work`（`analyze` / `followup` / `me`）。

未做：微信登录、生产短信、App Store、文言讲解层。运维见 `docs/deploy.md`。
