# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞。

## 怎么跑

```bash
open Yizhidao.xcodeproj
# 或
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

iOS 17+ / Xcode 15+。Bundle：`com.yizhidao.app`。全 App **固定浅色**宣纸主题（`AppTheme` + `preferredColorScheme(.light)`）。

## 技术栈

SwiftUI + SwiftData；无第三方依赖。经文在 `Hexagrams.json`（《易经证释》所引）。

## 目录与约定

- `Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `Domain/ReadingGuide`：多动爻主看焦点；`ResultView` 本卦/之卦 tab
- `Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选
- `App/AppNavigation`：结果页「同类」跳转历史同卦（预选方法与筛选）
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦：可摇 / 「选」手选四象；六爻上→初
- 三数：一键随机；未满三正整数则「起卦」禁用；「清空」始终可点
- 起卦页可折叠「起卦礼仪」；历史可改所问与验证、可左滑删除
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`AppIcon-source.png`

## 当前状态 / 下一步

已实现：双法起卦、礼仪、结果 tab＋主看（卦辞/彖曰/象曰/六爻）、历史聚合／验证／同类跳转、浅色主题。分支 `feature/result-reading-tabs`。未做：App Store、文言讲解层。
