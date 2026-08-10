# 易知道

原生 SwiftUI iOS App：数字起卦（三数 / 时间）与六爻金钱卦，记录占时并展示卦象、卦辞、动爻爻辞。

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

- **数字起卦 · 三数**：三个输入框 + 各自独立随机按钮，再起卦
- **数字起卦 · 时间**：农历年支、月、日 + **十二时辰**（子=1 … 亥=12）取数；界面用中文 DatePicker 选占问时刻
- **金钱卦**：逐爻摇或一键摇满（上爻在上、初爻在下）
- 自动保存占卦时间与结果到「历史」；结果页展示本卦/之卦、**卦辞、大象、动爻爻辞与小象**（经文据《易经证释》所引）

## 目录

| 路径 | 内容 |
|---|---|
| `Yizhidao/Engines/` | 数字 / 金钱起卦、京房卦序、农历时辰 |
| `Yizhidao/Features/` | 起卦 UI、结果、历史 |
| `Yizhidao/Resources/Hexagrams.json` | 64 卦名、卦辞、爻辞 |
| `YizhidaoTests/` | 起卦与时辰单测 |
