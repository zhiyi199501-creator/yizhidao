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
        include("Hexagrams.json", "cases.json", "YijingIntro.json", "ImaExplanations.json")
    }
    into(generatedAssets)
}

android {
    namespace = "com.yizhidao.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yizhidao.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // 模拟器连本机后端用 10.0.2.2；真机 Debug 改成 Mac 局域网 IP（ipconfig getifaddr en0）。
            buildConfigField("String", "API_BASE_URL", "\"http://172.20.10.10:8080\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://yzh.codedance.work\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
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
    debugImplementation(libs.androidx.compose.ui.tooling)
}
