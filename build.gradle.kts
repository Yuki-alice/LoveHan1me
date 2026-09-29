// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization) apply false
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    id("com.mikepenz.aboutlibraries.plugin") version "15.0.4" apply false
}

// materialkolor 5.0.1 的 iOS 产物把 ajalt/colormath 声明成了 jitpack 坐标
// (com.github.ajalt:colormath)，该坐标在 jitpack 上不存在；其在 Maven Central 的
// 真实坐标为 com.github.ajalt.colormath:colormath。重定向以修复 iOS 依赖解析。
subprojects {
    dependencies {
        modules {
            module("com.github.ajalt:colormath") {
                replacedBy("com.github.ajalt.colormath:colormath")
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
