// :player —— 播放内核独立模块（Gate3-P1，方案A）。
//
// 装载：PlaybackEngine 接口 + 5 引擎 + ComposePlaybackController +
// 纯弹幕引擎（Engine/PositionTracker/Layer/RenderOptions）+ 超分胶水（MpvShaders/ExoSuperResolution）。
// 包名不变（仍 lovehan1me.*），只换模块。video UI 留在 :shared，经 api 消费。
// target / 源集层级 / 单测宿主由 han1me-kmp-library 约定插件提供，本文件只保留模块特有配置。

plugins {
    id("han1me-kmp-library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.coroutines.core)
            // 版本对齐：mediamp 0.3.2 传递依赖的老 serialization 无本地缓存，
            // :shared 用 serialization.json 直接依赖抬到新版，这里同步（否则离线解析失败）
            implementation(libs.serialization.json)
            implementation(compose.components.resources)
            // 传递依赖对齐（与 :shared 的解析结果一致，jar 均有缓存）：
            // mediamp-api 拖入 datetime 0.7.1 / io-core 0.8.2，:shared 靠 datetime
            // 直接依赖 + ktor 带入的 io 抬到 0.8.0 / 0.9.1；:player 无 ktor，
            // 故显式声明（API 面用不到，仅对齐版本）。
            implementation(libs.datetime)
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
        }

        androidMain.dependencies {
            // 版本对齐：mediamp 传递的 material3 老版本无本地缓存，
            // 用 :shared 同款直接依赖抬到新版（仅 mediamp 所在的源集需要）。
            implementation(libs.cmp.material3)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.effect)
            implementation(libs.mpv.lib)
            implementation(libs.core.ktx)
            implementation(libs.coroutines.android)
        }

        // jvm("desktop") 自定义目标名，需用 by getting（与 :shared 同例）
        val desktopMain by getting {
            dependencies {
                // 同 androidMain 的版本对齐理由（mediamp-mpv-desktop 传递老 material3）。
                implementation(libs.cmp.material3)
                implementation(libs.mediamp.mpv.desktop)
                runtimeOnly(libs.mediamp.mpv.runtime)
                implementation(libs.coroutines.swing)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
        }

        val iosTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // IosAVPlaybackEngineTest 用 Darwin 引擎拉 Apple 公开测试流（与 :shared 同例）。
                implementation(libs.ktor.client.darwin)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Skia 原生库：ImageComposeScene 离屏渲染要 skiko，无注入即
                // ExceptionInInitializerError（与 :shared 同例）。
                // 版本必须与 compose 带来的 skiko 对齐（0.150.1）。
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.150.1")
            }
        }
    }
}

// composeRes 独立 Res 类：包名必须与 :shared 的 "lovehan1me" 不同，
// 否则两模块的 Res 类同包同名会在 classpath 上冲突。
compose.resources {
    publicResClass = true
    packageOfResClass = "lovehan1me.player"
}
