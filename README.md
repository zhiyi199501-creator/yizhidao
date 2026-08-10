# 易知道

原生 SwiftUI iOS App：数字起卦（三数 / 时间）与六爻金钱卦，记录占时并展示卦象与经文。

## 要求

- Xcode 15+ / iOS 17+
- macOS 上打开 `Yizhidao.xcodeproj`

## 打开与运行

```bash
open Yizhidao.xcodeproj
```

选择任意 iPhone Simulator，⌘R 运行。

## 测试

```bash
xcodebuild test -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

## 功能

- **数字起卦 · 三数**：三个输入框 + 各自独立随机 / 清空
- **数字起卦 · 时间**：默认农历年支、月、日 + **十二时辰**；可开「公历取数」（公历月日 + 1–24 时）；占问时刻选择框显示 `yyyy-MM-dd HH:mm`，弹层为中文日历
- **金钱卦**：逐爻或一键摇满（上爻在上、初爻在下）
- **结果**：本卦 / 之卦分 tab；各含卦辞、大象、六爻（上→初）；动爻红字；多动按规则标「主看」；经文据《易经证释》所引
- **历史**：SwiftData 保存占时与结果

## 协作

- 远端：https://github.com/zhiyi199501-creator/yizhidao
- `main` 受保护：勿直推，经 PR 合并

## 目录

| 路径 | 内容 |
|---|---|
| `Yizhidao/Engines/` | 数字 / 金钱起卦、京房卦序、农历／公历时辰 |
| `Yizhidao/Domain/` | 模型与 `ReadingGuide` 解卦焦点 |
| `Yizhidao/Features/` | 起卦 UI、结果、历史 |
| `Yizhidao/Resources/Hexagrams.json` | 64 卦：卦辞、大象、爻辞、小象 |
| `YizhidaoTests/` | 起卦、时辰、`ReadingGuide` 单测 |
