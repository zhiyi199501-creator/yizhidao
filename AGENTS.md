# 易知道 — Agent 入口

原生 iOS「易知道」：梅花式数字起卦 + 六爻金钱卦，玩占观辞；可选后端登录与 AI 解读。

## 怎么跑

```bash
open ios/Yizhidao.xcodeproj
# 或
xcodebuild test -project ios/Yizhidao.xcodeproj -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

安卓引擎单测（只需 JDK 17）：

```bash
cd android && ./gradlew :engines:test
```

用 Android Studio 打开 `android/` 跑 App。后端本地：`./start-backend.sh`。iOS 17+ / Xcode 15+。Bundle / applicationId：`com.yizhidao.app`。全 App **固定浅色**宣纸主题。

## 技术栈

- **App（iOS）**：SwiftUI + SwiftData；无第三方依赖。经文 `Hexagrams.json`（卦爻辞取证释；文言／四传并入同文件）；案例 `cases.json`
- **App（Android）**：Kotlin + Jetpack Compose；生产 HTTPS 用 **Cronet**（`AppHttp`，勿改回 `HttpURLConnection`）。引擎在 `android/engines`（纯 JVM，与 iOS 单测对拍）。见 `android/README.md`
- **后端**：`backend/` FastAPI + SQLite；短信登录（开发期固定码）与 AI（`AI_MODE=mock|openai`）

## 目录与约定

- Tab 顺序：起卦 / 历史 / 案例 / 我的
- iOS 工程在 `ios/`（与 `android/` 同级）：`Yizhidao.xcodeproj`、`Yizhidao/`、`YizhidaoTests/`
- `ios/Yizhidao/Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `ios/Yizhidao/Domain/ReadingGuide`：多动爻主看焦点；本卦/之卦展示在 `HexagramReadingBody`（结果页与案例详情共用）
- `ios/Yizhidao/Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选；删除进回收站
- `ios/Yizhidao/Features/Cases/`：按卦列表（无时间、无应验徽章）；iOS 打开时 `GET /v1/cases` 拉最新，失败则用包内/缓存；详情为背景 / 所问 / 验证 / 讲师解读 + 本卦之卦
- 案例底稿：`ios/Yizhidao/Resources/cases.json`。编辑根目录 `案例编辑表.xlsx`（gitignore）→ `python3 scripts/import_cases.py`；导出 `python3 scripts/export_cases.py`。补占编号按实际文王序，括号标讲座来源（如 `01-3乾卦三爻（从大有卦三爻讲）`）
- 经文底稿：`ios/Yizhidao/Resources/Hexagrams.json`。编辑根目录 `易经正文编辑表.xlsx`（gitignore）→ `python3 scripts/import_jingwen.py`；导出 `python3 scripts/export_jingwen.py`
- 《易经证释》阅读稿：`ios/Yizhidao/Resources/Zhengshi.json`。源文件不入库；更新时 `python3 scripts/import_zhengshi.py [全册.doc]`；代码（`ZhengshiStore`/阅读页）还在，但「我的」菜单暂未挂入口
- **IMA 黄庭书院讲解**（本分支）：点经文可看知识库讲解。覆盖卦辞／彖／大象／爻辞+小象（成对）、**用九／用六（成对）**、**文言**（乾坤）。包内 `ios/Yizhidao/Resources/ImaExplanations.json`；源采集在 gitignore 的 `data/ima-explanations/`。导出：`python3 scripts/export_ima_explanations.py`。Android 经 `copyIosAssets` 拷同文件。ID：`{nn}-guaci|tuanci|daxiang|wenyan|yong`、`{nn}-yao-{0…5}`（初=0）。入口：结果／案例／六十四卦详情（文言与用九用六仅六十四卦详情有）
- `ios/Yizhidao/App/`：`AppNavigation`、`AppTheme`、登录与「我的」（多在 `YizhidaoApp.swift`）。「我的」：资料、**保存的AI解读**（本地保存的解读）、**易经基础入门** `YijingIntro.json`、**易经六十四卦 / 易经四传** 读 `Hexagrams.json`（详情卡片标题**彖辞** / **大象**，正文带「彖曰：」「象曰：」前缀）、设置（回收站，清空需确认；退出登录）
- Android「保存的AI解读」与回收站对齐 iOS 分组列表：白卡片竖排卦名／时间／所问；解读左滑删除，回收站左滑恢复＋彻底删除。勿改回设置项左右排布；删除钮未滑开不得透出
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时
- 金钱卦：可摇 / 「选」手选四象；画面上爻在上、初爻在下；自下而上摇（先初后上）
- 三数：一键随机；未满三正整数则「起卦」禁用；「清空」始终可点
- 结果页悬浮 **AI**（需登录）：初次结构化解读，可追问/补背景；合适则 **保存** 到「我的 → 保存的AI解读」。**已保存**后追问成功自动更新；**重新解读**后右上角为「重新保存」。提示词见 `backend/app/services/ai.py`（含本卦初–上案例作参照）
- Debug API：iOS 模拟器 `127.0.0.1:8080`，真机改 `AuthAPI` 局域网 IP；安卓 Debug 改 `android/app/build.gradle.kts`。**Release** 两端 → `https://yzh.codedance.work`（安卓须 Cronet + Build Variant = release）。不要用已废的 `yizhidao.codedance.work`
- 生产 TLS/HTTP/3 避坑：`.cursor/rules/prod-tls-http3.mdc`、`docs/deploy.md`
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`.workbuddy/`、`AppIcon-source.png`、`backend/.env`、`backend/*.db`、`案例编辑表.xlsx`、`易经正文编辑表.xlsx`、`张庆祥讲易经案例_txt/`、`Yizhidao.xcscheme` 里把 Run 改成 Release 的本机偏好

## 当前状态 / 下一步

**main / 生产（PR #4，2026-08-17 源站核过；2026-08-19 iPhone 11 改走 `yzh`）**：双法起卦、礼仪、主看、历史、案例按卦、登录、`GET /v1/me`、AI 解读＋追问＋本地保存。iOS Release API：`https://yzh.codedance.work`。旧域名 `https://yizhidao.codedance.work` 电脑仍可用，部分 iPhone 11 打不开。`GET /v1/cases` 313 条（谦卦 0）。iOS 打开案例页拉取；失败用包内/缓存。

未做：微信登录、生产短信、App Store。运维见 `docs/deploy.md`。

**Android（本分支 `feature/classic-yijing-me`）**：Compose 四 Tab 已对齐 iOS 主路径（起卦／历史／案例／我的、IMA、短信登录、AI 解读）。Release 走 Cronet → `https://yzh.codedance.work`（2026-08-20 红米 Note 17 登录已通）。连生产与 TLS 事故见 `android/README.md`、`docs/deploy.md`「Android / Cronet」。未做：微信登录。
