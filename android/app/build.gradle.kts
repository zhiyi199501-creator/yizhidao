import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val iosResources = rootProject.projectDir.resolve("../ios/Yizhidao/Resources")
val generatedAssets = layout.buildDirectory.dir("generated/iosAssets")

val copyIosAssets by tasks.registering(Copy::class) {
    from(iosResources) {
        // 不含 Zhengshi.json：安卓暂无《易经证释》入口，避免白白增大 APK。
        include("Hexagrams.json", "cases.json", "YijingIntro.json", "YijingIntro.en.json", "ImaExplanations.json")
    }
    into(generatedAssets)
    // 清掉历史残留（曾整目录拷过 Zhengshi）。
    doFirst {
        generatedAssets.get().asFile.resolve("Zhengshi.json").delete()
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.yizhidao.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yizhidao.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "0.1.11"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            // 模拟器连本机后端用 10.0.2.2；真机 Debug 改成 Mac 局域网 IP（ipconfig getifaddr en0）。
            buildConfigField("String", "API_BASE_URL", "\"http://172.20.10.10:8080\"")
            // Web OAuth client ID（Credential Manager）；与后端 GOOGLE_CLIENT_IDS 中的 Web ID 一致。
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"259566448600-b7hai5qdlc0nl2c3r699q2f38k026i9j.apps.googleusercontent.com\"",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"https://api.yiwanjia.work\"")
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"259566448600-b7hai5qdlc0nl2c3r699q2f38k026i9j.apps.googleusercontent.com\"",
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有 keystore.properties 用正式钥；否则仍用 debug，避免本机没钥时编不过。
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // 侧载真机包：只带 arm64，去掉模拟器 x86 / 旧 32 位 ARM。
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets.getByName("main").assets.srcDir(generatedAssets)
}

tasks.named("preBuild") {
    dependsOn(copyIosAssets)
}

dependencies {
    implementation(project(":engines"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.cronet.embedded)
    implementation(libs.androidx.credentials.core)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid.lib)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
