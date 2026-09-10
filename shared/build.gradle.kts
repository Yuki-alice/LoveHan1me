@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// KMP 共享模块。
// target / 源集层级 / Android 命名空间等全部由 build-logic 的 han1me-kmp-library
// 约定插件统一提供（借鉴 animeko 的 ani.kmp-library），本文件只保留模块特有的配置：
// Compose 插件、iOS framework 产物、各源集的第三方依赖。

plugins {
    id("han1me-kmp-library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    // Room KMP：KSP 代码生成 + androidx.room 插件负责跨 target 接线
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
}

// Room 编译器按 target 注入（配置名由 KSP 为各 target 生成）
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// Room 官方插件强制要求声明 schema 输出目录（即使 exportSchema=false）
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    // P6b：CMP material3（1.12.0-alpha03）把 ListItem 等标为实验 API；
    // :app 用的 jetpack material3 1.5.0-alpha25 已转正，故这里显式 opt-in 保持源码一致
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }

    // iOS 框架导出：iOS 工程通过 import ComposeApp 使用共享 UI
    // （target 由约定插件按 gradle.properties 的 han1me.ios.enabled 声明）
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform：包名仍是 androidx.compose.*，与 Jetpack Compose 源码兼容
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
            implementation(libs.cmp.ui)

            // Lifecycle / ViewModel：JetBrains 移植版，全平台可用
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)

            // Navigation 3：UI 用 JetBrains 移植版，Runtime 用 Google 原版（本身即 KMP）
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.navigation3.runtime.cmp)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)

            // 协程 / 序列化 / 时间
            implementation(libs.coroutines.core)
            implementation(libs.datetime)
            implementation(libs.serialization.json)

            // 网络：Ktor 客户端（各平台引擎在对应源集声明）
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // HTML 解析：ksoup（Fleeksoft 坐标，jsoup 兼容 API，P4 起使用）
            implementation(libs.ksoup)
            // M5-5：CF 验证多端 WebView（iOS=WKWebView，desktop=KCEF）
            implementation(libs.compose.webview.multiplatform)

            // P6b：Toast（sonner 0.4.0 本身是 CMP 库）
            implementation(libs.sonner)

            // Compose 资源（P4：Res 类 / 最小字符串集）
            implementation(compose.components.resources)

            // 设置存储：DataStore Preferences 的多平台 core（P2b：DataStoreManager 已下沉）
            implementation(libs.datastore.preferences.core)

            // 数据库：Room 多平台（P2：MiscellanyDatabase spike 已接线 KSP 代码生成）
            // api 导出：:app 消费 MiscellanyDatabase/HKeyframeDao 需 RoomDatabase 等类型在编译 classpath
            api(libs.room.runtime.kmp)
            api(libs.sqlite.bundled)

            // 图片：Coil 3 本就多平台，网络层改用 ktor3 实现
            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor3)

            // P6d-4E：开源许可页（15.2.0 起 core/compose 为 KMP 产物，要求 Compose 1.12/Kotlin 2.4 对齐）
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
        }

        androidMain.dependencies {
            // Android 端沿用 OkHttp 引擎，可把现有 HDns / DoH / HCookieJar /
            // HProxySelector / Cloudflare 拦截器整条链路直接注入 Ktor，避免重写
            implementation(libs.ktor.client.okhttp)

            // SharedPreferences → DataStore 的历史迁移只在 Android 侧存在
            implementation(libs.datastore.preferences)

            // P6a：LanguageHelper android actual 用 AppCompatDelegate.getApplicationLocales
            implementation(libs.appcompat)

            // P5-1：播放器引擎归位 androidMain（坐标从 :app 照搬）
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.mpv.lib)
            // 引擎编译必需（:app 经 bundles.android.base 间接持有，此处显式声明）：
            // core-ktx（androidx.core.net.toUri）+ coroutines-android（Dispatchers.Main）
            implementation(libs.core.ktx)
            implementation(libs.coroutines.android)

            // P6d-1-C：动态取色（Kyant0 m3color，无 KMP 坐标，仅 androidMain；坐标从 :app 照搬）
            implementation(libs.kyant.m3color)
        }

        // jvm("desktop") 是自定义目标名，Gradle 不生成类型化访问器，需用 by getting
        val desktopMain by getting {
            dependencies {
                // 桌面端是 JVM，同样可用 OkHttp 引擎复用现有拦截器链
                implementation(libs.ktor.client.okhttp)
                // M1：Dispatchers.Main 的桌面实现（Swing EDT，供 viewModelScope）。
                // 此前误放在 commonMain，导致 iOS 元数据编译解析失败（swing 只有 JVM 变体）。
                implementation(libs.coroutines.swing)
                // M3：桌面 mpv 引擎（mediamp，含 Skia 渲染 + 各 OS native）
                implementation(libs.mediamp.mpv.desktop)
                // M3：mpv 原生库运行时（按平台解包；mediamp-mpv-runtime 聚合全平台）
                runtimeOnly(libs.mediamp.mpv.runtime)
            }
        }

        // jvmMain：android + desktop 共享的 JVM 中间源集（P3：OkHttp 拦截器链 / ServiceCreator 落点）
        val jvmMain by getting {
            dependencies {
                // 拦截器链直接使用 OkHttp（Ktor OkHttp 引擎 preconfigured 复用同一批 client）
                implementation(libs.okhttp)
                implementation(libs.okhttp.dns.over.https)
                // P6d-2：getchu 特化图片加载器（OkHttpNetworkFetcherFactory，jvm 专用）
                implementation(libs.coil.network.okhttp.kmp)
            }
        }

        iosMain.dependencies {
            // iOS 端只能走 Darwin 引擎，自定义 DNS / DoH 需降级为系统解析
            implementation(libs.ktor.client.darwin)
        }

        // M7-4：iOS 播放引擎真实验证（iosSimulatorArm64Test 跑在模拟器上）
        val iosTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// P4：composeRes 资源基建（公共 Res 类 + 固定包名；:app 的 R 与 shared composeRes 双轨，P6 收敛）
compose.resources {
    publicResClass = true
    packageOfResClass = "lovehan1me"
}
