# 易玩家 — Agent 入口

原生 iOS / Android「易玩家」（仓库 / Bundle 仍 `yizhidao`）：梅花式数字起卦 + 六爻金钱卦，玩占观辞；可选后端登录与问答。**仅海外上架**（排除中国大陆）。

## 怎么跑

```bash
open ios/Yizhidao.xcodeproj
# 或
xcodebuild test -project ios/Yizhidao.xcodeproj -scheme Yizhidao -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath .derivedData -quiet
```

`xcodebuild` 报需要完整 Xcode 时，把命令行指到 App（整机一次即可）：`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`。只装 Command Line Tools 编不了 iOS。

安卓引擎单测（只需 JDK 17）：

```bash
cd android && ./gradlew :engines:test
# App 单测（含 IMA / AI 正文分段）需 Android SDK：
./gradlew :app:testDebugUnitTest
```

后端单测：`cd backend && .venv/bin/python -m unittest`。本机抽检真实模型：`cd backend && .venv/bin/python scripts/eval_ai_reading.py`（需 `.env` 的 key；`--dry-run` 只看槽位）。运营后台：`ADMIN_PASSWORD` + `cd admin && npm run dev` → `http://127.0.0.1:5173/admin/`（须后端已起）。

用 Android Studio 打开 `android/` 跑 App。后端本地：`./start-backend.sh`。iOS 17+ / Xcode 15+。Bundle / applicationId：`com.yizhidao.app`。全 App **固定浅色**宣纸主题。

## 技术栈

- **App（iOS）**：SwiftUI + SwiftData；无第三方依赖。经文 `Hexagrams.json`（卦爻辞取证释；文言／四传并入同文件）；案例 `cases.json`。繁简跟系统语言（台湾／香港／澳门为繁体），无应用内开关。
- **App（Android）**：Kotlin + Jetpack Compose；生产 HTTPS 用 **Cronet**（`AppHttp`，勿改回 `HttpURLConnection`）。引擎在 `android/engines`（纯 JVM，与 iOS 单测对拍）。见 `android/README.md`
- **后端**：`backend/` FastAPI + SQLite；登录为 Apple / Google / 邮箱 OTP（短信路由仍保留，App 不展示）；AI（`AI_MODE=mock|openai`）；内部 `admin/`（Vite，Cookie 鉴权）

## 目录与约定

- Tab 顺序：起卦 / 历史 / 问答 / 我的
- iOS 工程在 `ios/`（与 `android/` 同级）：`Yizhidao.xcodeproj`、`Yizhidao/`、`YizhidaoTests/`
- `ios/Yizhidao/Engines/`：`DigitalCastingEngine`、`CoinCastingEngine`、`KingWenTable`、`LunarCalendarHelper`
- `ios/Yizhidao/Domain/ReadingGuide`：多动爻主看焦点；本卦/之卦展示在 `HexagramReadingBody`（结果页与案例详情共用）
- `ios/Yizhidao/Features/History/`：时间线 / 按卦；同卦明细内数字按动爻位、金钱按 0–6 动筛选；删除进回收站
- `ios/Yizhidao/Features/Cases/`：入口在「我的」；按卦列表（无时间、无应验徽章）；iOS 打开时 `GET /v1/cases` 拉最新，失败则用包内/缓存；详情为背景 / 所问 / 验证 / 讲师解读 + 本卦之卦
- 案例底稿：`ios/Yizhidao/Resources/cases.json`。日常在运营后台「案例」编辑工作副本并**发布**（立刻热更新 App `GET /v1/cases`）。导出 JSON 再提交包内文件，供下次发 App。可选导入旧「案例编辑表.xlsx」或 JSON；Excel 不再当日常编辑器。补占编号按实际文王序，括号标讲座来源（如 `01-3乾卦三爻（从大有卦三爻讲）`）
- 经文底稿：`ios/Yizhidao/Resources/Hexagrams.json`。编辑根目录 `易经正文编辑表.xlsx`（gitignore）→ `python3 scripts/import_jingwen.py`；导出 `python3 scripts/export_jingwen.py`
- 《易经证释》阅读稿：`ios/Yizhidao/Resources/Zhengshi.json`。源文件不入库；更新时 `python3 scripts/import_zhengshi.py [全册.doc]`；代码（`ZhengshiStore`/阅读页）还在，但「我的」菜单暂未挂入口
- **基础入门**：`ios/Yizhidao/Resources/YijingIntro.json` 九章（含「怎样起卦」：三数／时间／金钱四象，与起卦页一致，不写 ÷8／÷6）。块 `p`／`quote`／`list`／`table`／`figure`／`links`；册页阅读，章末上一章／下一章同一行。勿再按经文卡片切段。Android `copyIosAssets` 拷同文件
- **IMA 黄庭书院讲解**：点经文可看知识库讲解。覆盖卦辞／彖／大象／爻辞+小象（成对）、用九／用六（成对）、文言（乾坤）。包内 `ios/Yizhidao/Resources/ImaExplanations.json`；源采集 gitignore `data/ima-explanations/`。后台「黄庭」可改 `answer`：**立刻影响服务端 AI**；App 弹层要下次发版。导出 `python3 scripts/export_ima_explanations.py` **会覆盖**手改，改过后别跑。Android `copyIosAssets` 拷同文件（**不含** `Zhengshi.json`）。ID：`{nn}-guaci|tuanci|daxiang|wenyan|yong`、`{nn}-yao-{0…5}`（初=0）。入口：结果／案例／六十四卦详情（文言与用九用六仅六十四卦详情有）
- **IMA 展示**：包内 `ImaExplanations.json` 原稿已去掉出处后标／整行「思考过程」；App `ImaAnswerFormatter` 与后台读取仍再洗一遍。「表格」标记与 markdown 表画成表。安卓弹层用全屏 Popup（约 93% 高，盖住页头），下拉超过 **1/4** 收起，点遮罩不关。iOS 宣纸 `AppTheme` 已为 OLED 调淡；弹层 `.presentationBackground` 用同一渐变
- `ios/Yizhidao/App/`：`AppNavigation`、`AppTheme`、登录与「我的」（多在 `YizhidaoApp.swift`）。「我的」：资料、**基础入门**、**六十四卦 / 四传**、**案例**、设置（按键音效、回收站，清空需确认；退出登录；**注销账号**调 `DELETE /v1/me`）。经文详情卡片标题**彖辞** / **大象**，正文带「彖曰：」「象曰：」前缀。「问答」Tab 列出全部本地问答（一占一条，自动保存）。包内与生产页：隐私政策 / 用户协议；公网 `https://api.yiwanjia.work/{privacy,terms,support}`
- **二级页藏底栏**：结果、问答详情、历史同卦／记录、我的子页。iOS `parchmentBackground()` 默认藏 Tab，四个 Tab 根页和 IMA sheet 传 `hidesTabBar: false`。Android `onTabBarVisible`。登录 sheet 里的协议页不要藏 Tab
- Android「问答」与回收站对齐 iOS 分组列表：白卡片竖排卦名／时间／所问；问答左滑删除，回收站左滑恢复＋彻底删除。勿改回设置项左右排布；删除钮未滑开不得透出
- **经文勿换他本**；改解卦规则先改 `ReadingGuide` 并补测
- 繁简只跟系统语言 + `.zh`。禁止 hook `UILabel` / `UIButton` / `Bundle.main`（iPhone 11 弹键盘会卡）。点空白收键盘须在手势 `shouldReceive` 跳过输入框；登录数字框用 `UITextField`，勿加 `textContentType` 自动填充
- **主看 UI**：0 动→本卦卦辞；2 动→本卦上动爻；3 动→本卦卦辞；4 动→之卦下静爻；5 动→之卦静爻；6 动→之卦卦辞；1 动不标「主看」
- 时间起卦默认十二时辰；「公历取数」→公历月日 + 1–24 时。UI 说明用「以当前时刻起卦，或者选择某个时刻起卦」，不要写 ÷8／÷6 公式
- 金钱起卦：可摇 / 「选」手选四象；画面上爻在上、初爻在下；自下而上摇（先初后上）。无「一键摇满」；说明「用三枚铜钱摇六次，自下而上成卦」在清空左侧，清空靠右
- 起卦页所问必填；空则「起卦」禁用
- 三数：各行「随机」；无「一键随机」；未满三正整数则「起卦」禁用。「清空」始终可点、靠右，左侧「从上往下输入3个数起卦」
- 结果页与问答详情右上角 **同类**（同卦明细内已打开的结果不显示）。悬浮 **问**：该占已有问答则直接打开（不必登录）；没有则自动生成（需登录）。页标题 **问答**；一占一条、自动保存。点「可以接着问」直接发出。机制见 `docs/ai-reading.md`；接口见 `docs/backend-min-spec.md`
- **AI 展示**：`AIAnswerFormatter`（iOS / Android）只在展示层按句分段，不改存盘原文
- Debug API：iOS 模拟器 `127.0.0.1:8080`，真机改 `AuthAPI` 局域网 IP；安卓 Debug 改 `android/app/build.gradle.kts`（明文 HTTP 靠 `android/app/src/debug/res/xml/network_security_config.xml`，主配置会覆盖 `usesCleartextTraffic`）。**Release** 仅海外：`https://api.yiwanjia.work`。安卓须 Cronet + Build Variant = release。国内 iPhone 11 蜂窝直连不稳，开代理可通，勿靠轮换子域救场
- 登录页：iOS 主按钮 Apple、Android 主按钮 Google；邮箱在「其他登录方式」子页；点登录先检查协议。改 `.env` 后须重启后端；rsync 源若是 `backend/` 须 `--exclude '.env'`（排除 `backend/.env` 挡不住）
- 生产 TLS/HTTP/3 避坑：`.cursor/rules/prod-tls-http3.mdc`、`docs/deploy.md`
- **`main` 保护**：禁止直推，经 PR 合并（https://github.com/zhiyi199501-creator/yizhidao）
- 勿提交 `.derivedData/`、`.firecrawl/`、`.workbuddy/`、`AppIcon-source.png`、`backend/.env`、`backend/*.db`、`案例编辑表.xlsx`、`易经正文编辑表.xlsx`、`张庆祥讲易经案例_txt/`、`Yizhidao.xcscheme` 里把 Run 改成 Release 的本机偏好

## 当前状态 / 下一步

**已合 main、生产未发（2026-08-28）**：AI 扩卡已进 `main`（[PR #12](https://github.com/zhiyi199501-creator/yizhidao/pull/12)）。生产 `api.yiwanjia.work` 仍是发版前的三字段解读；须重建镜像（含 `ImaExplanations.json`）并发 App 后才现役。

**内容后台（未合 main、生产未发）**：`admin/` 第二版（案例发布 / 黄庭改 answer / 经文只读 / 夹具抽检）。生产 `/admin/` **404**。合入并重建镜像前，生产更新案例仍 `docker compose cp`（见 `docs/deploy.md`）。

**App 信息架构（本分支未提交、未发）**：Tab **起卦 / 历史 / 问答 / 我的**；案例在「我的」；菜单 **基础入门 / 六十四卦 / 四传 / 案例**；基础入门九章册页（含怎样起卦）。结果页悬浮 **问**，页标题「问答」，一占一条自动保存。二级页藏底栏；起卦页无「一键随机／一键摇满」；问答详情右上角「同类」；AI 正文展示层分段。商店 / TestFlight 现役包仍是旧 Tab（起卦 / 历史 / 案例 / 我的）与手动保存。

**App Release（仅海外）**：新加坡 `124.156.192.137`，`https://api.yiwanjia.work/health` 200（SSH `yiwanjia`）。iOS / Android Release 均指向该域；**不上架中国区**，无需 ICP。现役栈是 `docker compose` + `Caddyfile.overseas`（拷成 `Caddyfile`），不是 `prod.yml`；解析 DNSPod，禁止橙云。运维见 `docs/deploy.md`。国内 iPhone 11 直连 443 不稳，开代理可通。

**国内后端（遗留，App 不再使用）**：`119.91.239.58` / `yzd.codedance.work` 仍可用于本地对照或日后另做国内产品；现役 App 不连。

**旧海外机（遗留）**：`43.128.104.104` / `yd.codedance.work`（2026-08-26 改为 H2-only），易知道 API 已迁出。

**试用登录（Debug）**：`EMAIL_PROVIDER=mock`。有 `DEV_EMAIL_FIXED_CODE` 时任意合法邮箱用该码；为空则随机码，终端 `[email:mock] … code=`（须 `print`，`logger.info` 在 uvicorn 下看不见）。Release：iOS **Apple / 邮箱**，Android **Google / 邮箱**。短信接口仍保留，App 登录页不展示。审核包**不要**配 `EMAIL_TEST_ADDRESSES`（白名单不发真邮件）。

**App Store（进行中）**：Connect App `com.yizhidao.app`（id `6804203617`）；品牌名 **易玩家**；排除中国大陆。法律 URL：`https://api.yiwanjia.work/{privacy,terms,support}`。待：TestFlight、IAP、提审。

未做：IAP 验单、正式 Android keystore、生产 `GOOGLE_CLIENT_IDS` / App `GOOGLE_WEB_CLIENT_ID`。mock 邮箱 `print`（`8e3bceb`）已随 PR #12 进 `main`。

**Android**：Compose 四 Tab 已对齐 iOS。Release 须 Cronet → `api.yiwanjia.work`（勿 `addQuicHint`）。见 `android/README.md`。
