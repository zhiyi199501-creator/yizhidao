# 易知道 Android

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

应与 iOS `ios/YizhidaoTests` 对齐：三数 / 时间起卦、金钱卦、文王序、主看规则、经文加载。

## 打开 App

用 Android Studio 打开 `android/` 目录。首次会提示安装 SDK。

有 SDK 后：

```bash
cd android
./gradlew :app:installDebug
```

`applicationId`：`com.yizhidao.app`。Release API：`https://yd.codedance.work`（与 iOS 相同；勿用 `yizhidao.codedance.work` / `yzh.codedance.work`）。生产 HTTPS 必须走 **Cronet**，不要改回 `HttpURLConnection`。

经文／案例／入门／IMA 讲解 JSON 构建时从 `ios/Yizhidao/Resources/` 拷贝（`copyIosAssets`），勿在 `android/` 另维护一份。重新导出讲解：`python3 scripts/export_ima_explanations.py`。

## 连生产

默认 Run 是 Debug，打 `app/build.gradle.kts` 的局域网地址（现 `http://172.20.10.10:8080`）。要打生产：Build Variants → `app` → **release**（无正式 keystore 时用 debug 签名），然后重装。登录页只有 Debug 显示「当前接口」。生产验证码在服务器日志，不是 `123456`。

浏览器能开 `/health` 不算过。事故与现役栈见 `docs/deploy.md`「Android / Cronet」。

## 当前范围

已实现：数字起卦（三数 / 时间）、金钱卦、本卦之卦与主看、本地历史与回收站、案例按卦（`GET /v1/cases` 热更新，失败用包内/缓存）、繁简跟系统语言、按键音效、我的页可读基础入门 / 六十四卦 / 四传（详情卡片标题彖辞/大象）、短信登录、资料编辑、AI 解读＋追问＋保存的AI解读（已保存后追问自动更新；重新解读→「重新保存」；分组列表竖排卦名／时间／所问，左滑删除）、设置（按键音效 / 回收站同结构，左滑恢复或彻底删除）、**IMA 黄庭书院讲解**（结果／案例／六十四卦详情点经文）。AI 页隐藏底部 Tab；Manifest `adjustNothing` + 追问栏 `imePadding`（配合 `enableEdgeToEdge()` 贴键盘）。连生产见上文。

未做：微信登录。
