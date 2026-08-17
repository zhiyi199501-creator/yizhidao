pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "Yizhidao"

include(":engines")

fun androidSdkAvailable(): Boolean {
    val local = file("local.properties")
    if (local.exists()) {
        val props = java.util.Properties()
        local.inputStream().use { props.load(it) }
        val dir = props.getProperty("sdk.dir")
        if (!dir.isNullOrBlank() && file(dir).isDirectory) return true
    }
    val env = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    return !env.isNullOrBlank() && file(env).isDirectory
}

if (androidSdkAvailable()) {
    include(":app")
}
