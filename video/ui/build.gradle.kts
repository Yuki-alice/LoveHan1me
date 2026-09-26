// :video:ui —— 视频 UI 层（Compose）。
//
// 硬边界：**不得依赖 mediamp**（类型会漏进 :shared 的 iOS framework export）、
// **不得依赖 :video:engine**（UI 不该看见引擎实现，引擎的类型只在 :shared 编排层接线）。
// 需要渲染面时由 :shared 从 :video:surface 取，经槽位传进来。
// 当前装载：弹幕绘制层（DanmakuLayer + 其离屏渲染测试）+ 播放器控件 + GIF 录制管线。

plugins {
    id("han1me-kmp-library")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    // 播放器控件用到 expressive 档（ContainedLoadingIndicator / IconButtonDefaults.shapes 等），
    // 与 :shared 同口径全局 opt-in，避免逐处 @OptIn。
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }

    sourceSets {
        commonMain.dependencies {
            // 弹幕几何（DanmakuEngine/Viewport/Slot 等）在契约层，且出现在本模块公开签名里。
            api(project(":video:contract"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.material3)
            implementation(libs.datetime)
            implementation(libs.coroutines.core)
            // GIF 编码（Gif89aEncoder）内部用 okio.Buffer 拼字节流
            implementation(libs.okio)
            // Res 类：本模块自持播放器控件的资源（包名 lovehan1me.video.ui，
            // 与 :shared 的 lovehan1me、:video:engine 的 lovehan1me.player 各不同包）。
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // GifRecorderTest 用 runBlocking
            implementation(libs.coroutines.core)
        }

        // DanmakuLayer 的两个离屏渲染用例走 ImageComposeScene，需要 skiko 原生库
        // （与 :video:engine / :shared 同例、同版本，必须与 compose 带来的 skiko 对齐）。
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Gif89aEncoderTest / MinimalGifDecoder 用 okio.Buffer 读字节流
                implementation(libs.okio)
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.150.1")
            }
        }
    }
}

// 播放器控件的资源归本模块：包名与 :shared 的 "lovehan1me"、:video:engine 的
// "lovehan1me.player" 都不同 —— 多个模块的 Res 类同包同名会在 classpath 上冲突。
compose.resources {
    publicResClass = true
    packageOfResClass = "lovehan1me.video.ui"
}