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

应与 iOS `YizhidaoTests` 对齐：三数 / 时间起卦、金钱卦、文王序、主看规则、经文加载。

## 打开 App

用 Android Studio 打开 `android/` 目录。首次会提示安装 SDK。

有 SDK 后：

```bash
cd android
./gradlew :app:installDebug
```

`applicationId`：`com.yizhidao.app`。Release API：`https://yizhidao.codedance.work`。

经文与案例 JSON 构建时从 `Yizhidao/Resources/` 拷贝，勿在 `android/` 另维护一份。

## 当前范围

已实现：数字起卦（三数 / 时间）、金钱卦、本卦之卦与主看、本地历史、案例按卦（包内底稿）、我的页可读基础入门、六十四卦与四传。

未做：短信/微信登录、AI 解读、回收站、案例服务端热更新。
