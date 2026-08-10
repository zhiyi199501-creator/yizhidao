# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞。

## 怎么跑

```bash
open Yizhidao.xcodeproj
# 或
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

iOS 17+ / Xcode 15+。Bundle：`com.yizhidao.app`。

## 技术栈

SwiftUI + SwiftData；无第三方依赖。经文在 `Hexagrams.json`（《易经证释》所引）。

## 目录与约定

- `Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `Domain/ReadingGuide`：多动爻主看焦点；`ResultView` 本卦/之卦 tab 展示
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦／结果六爻：上爻在上 → 初爻在下
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`

## 当前状态 / 下一步

已实现：双法起卦、公历开关、结果 tab＋主看、历史、图标。协作：`feature/result-reading-tabs` → PR。未做：App Store、文言讲解层。
