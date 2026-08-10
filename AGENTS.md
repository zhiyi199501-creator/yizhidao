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
- **时间起卦用十二时辰**（子1…亥12），禁止改回 1–24 时钟小时
- 金钱卦 UI：上爻在上 → 初爻在下；爻名「九二/六二」格式
- App Icon：`Assets.xcassets/AppIcon.appiconset/AppIcon.png`（淡黄底横排「易知道」）
- 勿提交 `.derivedData/`、`.firecrawl/`；仓库当前**尚未** `git init`

## 当前状态 / 下一步

已实现：双法起卦、结果+历史、时辰取数、图标。未做：App Store 上架、git 初始化、文言解读层以外的扩展玩法。改起卦规则先改引擎并补测。
