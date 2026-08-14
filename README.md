# 易知道

原生 SwiftUI iOS App：数字起卦（三数 / 时间）与六爻金钱卦，记录占时并展示卦象与经文。界面固定浅色宣纸风格。可选 FastAPI 后端：手机号登录与 AI 解读。

## 要求

- Xcode 15+ / iOS 17+
- macOS 上打开 `Yizhidao.xcodeproj`
- 联调登录 / AI：Python 3.9+（见 `backend/`）

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

- **起卦页**：可折叠「起卦礼仪」；所问可选
- **数字起卦 · 三数**：各框随机 + **一键随机** / 清空；三数未齐则「起卦」禁用
- **数字起卦 · 时间**：默认农历年支、月、日 + **十二时辰**；可开「公历取数」（公历月日 + 1–24 时）；占问时刻 `yyyy-MM-dd HH:mm`，弹层中文日历
- **金钱卦**：逐爻摇或「选」手选四象（少阳／少阴／阳动／阴动）；一键摇满；上爻在上、初爻在下
- **结果**：本卦 / 之卦 tab；卦辞、彖曰、象曰、六爻；动爻红字；「主看」；可改所问与验证；右上角「同类」；右下角悬浮 **AI** 解读（需登录）
- **历史**：SwiftData 本地；**时间** / **按卦**（文王序）；状态筛选；左滑删除进回收站；数字起卦单爻动在箭头上方标红字（初/二/三/四/五/上）
- **我的**：登录（微信待接入、手机号验证码对接后端）、资料编辑、回收站、AI 解读历史入口

## 协作

- 远端：https://github.com/zhiyi199501-creator/yizhidao
- `main` 受保护：勿直推，经 PR 合并

## 目录

| 路径 | 内容 |
|---|---|
| `Yizhidao/App/` | 入口、`AppTheme`、`AppNavigation`、登录与「我的」 |
| `Yizhidao/Engines/` | 数字 / 金钱起卦、京房卦序、农历／公历时辰 |
| `Yizhidao/Domain/` | 模型与 `ReadingGuide` 解卦焦点 |
| `Yizhidao/Features/` | 起卦 UI、结果（含 AI 页）、历史（含按卦） |
| `Yizhidao/Data/` | SwiftData `ReadingRecord`、回收站、经文加载 |
| `Yizhidao/Resources/Hexagrams.json` | 64 卦：卦辞、彖辞、大象、爻辞、小象 |
| `YizhidaoTests/` | 起卦、时辰、`ReadingGuide` 单测 |
| `docs/backend-min-spec.md` | 登录与 AI 分析后端最小接口草案 |
| `backend/` | FastAPI：短信登录 + AI 解读 |
| `start-backend.sh` | 一键启动后端 |

## 后端本地联调

```bash
./start-backend.sh
```

- iOS **Debug**：模拟器 → `http://127.0.0.1:8080`；真机 → Mac 局域网 IP（代码里 `AuthAPI`，查 IP：`ipconfig getifaddr en0`）
- 开发期验证码默认 `123456`
- AI：`backend/.env` 中 `AI_MODE=mock`（规则）或 `openai`（OpenAI 兼容，如 DeepSeek）；细节见 `backend/README.md`
