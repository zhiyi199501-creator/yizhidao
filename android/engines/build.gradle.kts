plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

val iosResources = rootProject.projectDir.resolve("../ios/Yizhidao/Resources")

val copyIosHexagrams by tasks.registering(Copy::class) {
    from(iosResources.resolve("Hexagrams.json"))
    into(layout.buildDirectory.dir("generated/iosResources"))
}

sourceSets {
    test {
        resources.srcDir(layout.buildDirectory.dir("generated/iosResources"))
    }
}

tasks.named("processTestResources") {
    dependsOn(copyIosHexagrams)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
