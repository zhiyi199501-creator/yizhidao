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

SwiftUI + SwiftData；无第三方依赖。卦辞数据在 `Hexagrams.json`。

## 目录与约定

- `Engines/`：纯逻辑（`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`）
- `Features/Casting|History/`：界面
- **经文版本以《易经证释》所引为准**（卦辞、大象、爻辞、小象）；勿擅自换用他本
- **多动爻解卦**按动爻数：0本卦辞；1本卦动爻；2本卦两动爻以上为主；3两卦卦辞以本为主；4之卦两静爻以下为主；5之卦静爻；6之卦卦辞（见 `ReadingGuide`）
- 时间起卦默认十二时辰（子1…亥12）；「公历取数」开启时用公历月日 + 1–24 时
- 金钱卦 UI：上爻在上 → 初爻在下；爻名「九二/六二」格式
- App Icon：`Assets.xcassets/AppIcon.appiconset/AppIcon.png`（淡黄底横排「易知道」）
- 勿提交 `.derivedData/`、`.firecrawl/`

## 当前状态 / 下一步

已实现：双法起卦、结果（卦辞/大象/动爻爻辞与小象）+历史、时辰/公历取数、图标。未做：App Store 上架、文言讲解层。改起卦规则先改引擎并补测。
