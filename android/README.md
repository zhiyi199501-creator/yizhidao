# 易玩家 Android

原生 Kotlin 客户端：引擎与 iOS 同源规则，界面用 Jetpack Compose 复刻宣纸主题。

## 要求

- JDK 17+（`echo $JAVA_HOME` 指向 17 即可）
- Android Studio Ladybug+ 或 Android SDK 35（跑 App 时）
- 引擎单测只需 JDK，不需要 Android SDK

国内网络下 Wrapper 使用腾讯云 Gradle 镜像；依赖仓库优先阿里云，失败再回落到 Google / Maven Central。若要改回官方：

`android/gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl`。

## 引擎测试

```bash
cd android
./gradlew :engines:test
```

应与 iOS `ios/YizhidaoTests` 对齐：三数 / 时间起卦、金钱起卦、文王序、主看规则、经文加载。

## 打开 App

用 Android Studio 打开 `android/` 目录。首次会提示安装 SDK。

有 SDK 后：

```bash
cd android
./gradlew :app:installDebug
```

`applicationId`：`com.yizhidao.app`。Release API：`https://api.yiwanjia.work`（仅海外 Google Play；**不上架国内商店**）。生产 HTTPS 必须走 **Cronet**，不要 `addQuicHint`，不要改回 `HttpURLConnection`。

经文／案例／入门／IMA 讲解 JSON 构建时从 `ios/Yizhidao/Resources/` 拷贝（`copyIosAssets`），勿在 `android/` 另维护一份。**不含** `Zhengshi.json`（安卓暂无证释入口）。服务端讲解走运营后台「黄庭」（立刻影响 AI）；App 弹层读包内 JSON，要发版。`python3 scripts/export_ima_explanations.py` 会覆盖手改，改过后别跑（导出会再洗后标／「思考过程」）。`ImaAnswerFormatter` 运行时仍洗一遍，表格画成表。App 单测：`./gradlew :app:testDebugUnitTest`。Release 侧载包：`arm64-v8a` + R8 minify（约 10MB）。

## 连生产

默认 Run 是 Debug（局域网 API）。要打生产：**Build Variants → release**，Cronet → `https://api.yiwanjia.work`。

浏览器能开 `/health` 不算过。事故与现役栈见 `docs/deploy.md`「Android / Cronet」。

## 当前范围

已实现：数字起卦（三数 / 时间只占此刻）、金钱起卦（起卦页系辞竖排 + 朱印，太极垫在句心；全屏仪式静心→告神→选法门→取数→揭卦，点「看辞」进结果）、本卦之卦与主看、本地历史与回收站、**问答** Tab（全部问答，一占一条自动保存，左滑删除）、案例在「我的」（`GET /v1/cases` 热更新，失败用包内/缓存）、语言跟系统（中文简繁，非中文界面壳英文）、我的页可读基础入门 / 六十四卦 / 四传 / 案例（详情卡片标题彖辞/大象）/ 意见反馈 / 检查更新、**邮箱（主）/ Google（其他登录方式）**（Debug/Release 均无短信入口）、资料编辑、问答＋追问（一篇回示：事情背景／当下／方向／建议，须防并入建议；追问后仍给建议与可再问；结果页所问只读可改、不展示取数；「问」：刚起完约 2 秒自动开一次，已有问答直接打开不重生成，没有则自动生成；页标题「问答」；详情右上角「同类」；列表本卦⟶之卦／时间／所问）、设置（回收站同结构，左滑恢复或彻底删除；已登录可**注销账号**）、**IMA 黄庭书院讲解**（结果／案例／六十四卦详情点经文；弹层约 93% 高，下拉超 1/4 收起，点遮罩不关）。六十四卦／四传页底「经文版本：《易经证释》所引」。数字取数进页不弹键盘。无按键音效。结果／问答详情／历史记录／我的子页隐藏底部 Tab；Manifest `adjustNothing` + 追问栏 `imePadding`（配合 `enableEdgeToEdge()` 贴键盘）。Debug 连局域网 `http://` 须 Debug 专用 `network_security_config`（`cleartextTrafficPermitted`）。连生产见上文。

Google 登录：在 `app/build.gradle.kts` 的 `GOOGLE_WEB_CLIENT_ID` 填入 Google Cloud **Web Client ID**（与后端 `GOOGLE_CLIENT_IDS` 一致）。另需 Android OAuth 客户端：包名 `com.yizhidao.app` + **Play 应用签名** SHA-1（内测/商店包）以及（若旁路安装）**上传密钥** SHA-1。刚装包后偶发要等 1–2 分钟；国内红米须已登录 Google 账号并开 VPN。详见 `backend/README.md`、`docs/deploy.md`。

Release 签名：本机 `android/keystore.properties`（gitignore）指向 `upload-keystore.jks`；示例见 `keystore.properties.example`。打内测包：`./gradlew :app:bundleRelease`（JDK 17/21；勿用 Android Studio 自带 JBR 25）。仓库现役 **0.1.11 / versionCode 12**；侧载页 `https://api.yiwanjia.work/download/`。

设置（已登录）含 **注销账号**（`DELETE /v1/me`），与 iOS 对齐。

iOS 买断问答（`UnlockStore` / `POST /v1/iap/verify`）安卓尚未接 Play Billing；生产默认 `ANDROID_COMPLIMENTARY_UNLOCK` 给安卓用户赠送解锁额度（接上结算后关掉）。
