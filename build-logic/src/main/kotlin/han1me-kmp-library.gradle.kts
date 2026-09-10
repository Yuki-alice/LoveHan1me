@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

/**
 * han1me-kmp-library：KMP 库模块统一约定插件（借鉴 animeko 的 ani.kmp-library）
 *
 * 职责：应用 KGP + AGP-KMP 插件，声明全部 target 与 source set 层级。
 * 模块自身只保留「第三方依赖 + 平台特有产物（如 iOS framework）」。
 *
 * 平台架构：
 * ```
 * common
 *   - jvm            ← desktop 与 Android 共享的 JVM 代码放 jvmMain（大幅减少 actual）
 *     - android
 *     - desktop
 *   - native
 *     - apple
 *       - ios
 *         - iosArm64
 *         - iosSimulatorArm64
 * ```
 *
 * 配置项见 gradle.properties（han1me.*）。
 * 注意：脚本顶层 val 在 precompiled script 里是生成类的成员属性，
 * 在 android {} lambda 内会被接收者同名属性遮蔽——命名务必避开 DSL 属性名。
 */

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // AGP 9 起 KMP 库模块必须用此插件；旧的 com.android.library 与 KMP 插件互斥。
    // 注意：该插件不支持 buildConfig / 变体 / externalNativeBuild，
    // 这些能力保留在 :app，或改用 BuildKonfig。
    id("com.android.kotlin.multiplatform.library")
}

val androidCompileSdk = providers.gradleProperty("han1me.android.compileSdk").getOrElse("37").toInt()
val androidMinSdk = providers.gradleProperty("han1me.android.minSdk").getOrElse("29").toInt()
val namespaceBase = providers.gradleProperty("han1me.namespace.base")
    .getOrElse("me.lovehan1me")
val iosEnabled = providers.gradleProperty("han1me.ios.enabled").getOrElse("true").toBoolean()

// namespace 由基础包名 + 模块路径自动派生：":shared" -> "<base>.shared"
val moduleNamespace = namespaceBase + project.path.replace(':', '.')

@Suppress("UnstableApiUsage")
configure<KotlinMultiplatformExtension> {
    android {
        namespace = moduleNamespace
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // 桌面端（Windows / macOS / Linux）
    jvm("desktop")

    // iOS 真机与模拟器；iosX64 已被 Compose Multiplatform 移除，不再声明
    if (iosEnabled) {
        iosArm64()
        iosSimulatorArm64()
    }

    // 自定义层级：desktop 与 Android 同属 JVM 中间层，可共享纯 JVM 代码
    applyDefaultHierarchyTemplate {
        common {
            group("jvm") {
                withJvm()
                group("android")
            }
            group("android") {
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    // Room KMP 会生成 expect/actual 类（如 XxxConstructor actual object），显式 opt-in 消除 Beta 警告
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // 桌面端统一字节码版本（androidTarget 非 KotlinJvmTarget，不会被误伤）
    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
