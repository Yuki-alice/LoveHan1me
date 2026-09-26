// :video:surface —— 宿主视频渲染面（Compose + mediamp）。
//
// 为什么单独一层：渲染面必须同时满足「是 @Composable」与「引用 mediamp 自家 Surface
// 及具体引擎实例」，而 :video:engine 禁 Compose（T9 守）、:video:ui 禁 mediamp
// （ModuleLayeringTest 守）——三条约束互斥，只能独立成层。
// **本模块是唯一允许 mediamp 与 Compose 共存的地方**；它的公开签名里不得出现
// mediamp 类型（`PlatformVideoSurface` 只吃 PlaybackEngine + Modifier），否则照样漏进
// :shared 的 iOS framework export。

plugins {
    id("han1me-kmp-library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // 渲染面要按具体引擎实例取 mediampPlayer，引擎类型在公开签名里。
            api(project(":video:engine"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            // 各端 concrete surface 的公共类型（MediampPlayer / VideoSurface 契约）。
            implementation(libs.mediamp.api)
        }

        androidMain.dependencies {
            // ExoPlayerMediampPlayerSurface + ExoPlayerMediampPlayer
            implementation(libs.mediamp.exoplayer)
        }

        val iosMain by getting {
            dependencies {
                // AVKitMediampPlayerSurface
                implementation(libs.mediamp.avkit)
            }
        }

        val desktopMain by getting {
            dependencies {
                // MpvMediampPlayerSurface（Skia 面）
                implementation(libs.mediamp.mpv.desktop)
            }
        }
    }
}