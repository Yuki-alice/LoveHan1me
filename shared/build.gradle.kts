@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
// 必须 import：Gradle Kotlin DSL 里写 `java.util.Properties` 会被解析成 project 的 java 扩展
import java.util.Properties

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
            // Gate3-P1：:player 是独立 KMP 库，iOS framework 必须 export，
            // 否则 Swift 侧看不到引擎类型（api 依赖不会自动进 framework）。
            export(project(":player"))
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

            // 备份导出/导入走 okio 流式读写（`openBackupSink/Source` 的签名即 okio 类型）
            implementation(libs.okio)

            // 网络：Ktor 客户端（各平台引擎在对应源集声明）
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // HTML 解析：ksoup（Fleeksoft 坐标，jsoup 兼容 API，P4 起使用）
            implementation(libs.ksoup)

            // Compose 资源（P4：Res 类 / 最小字符串集）
            implementation(compose.components.resources)

            // @Preview 注解（ui/preview 包）。注解是全平台的，但"渲染能力"要落在
            // 具体平台源集：Android 见 androidMain 的 ui-tooling。
            implementation(libs.cmp.ui.tooling.preview)

            // 设置存储：DataStore Preferences 的多平台 core（P2b：DataStoreManager 已下沉）
            implementation(libs.datastore.preferences.core)

            // 数据库：Room 多平台
            // api 导出：:app 侧历史曾消费 RoomDatabase 类型在编译 classpath，保留 api 导出
            api(libs.room.runtime.kmp)
            api(libs.sqlite.bundled)

            // 图片：Coil 3 本就多平台，网络层改用 ktor3 实现
            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor3)

            // P6d-4E：开源许可页（15.2.0 起 core/compose 为 KMP 产物，要求 Compose 1.12/Kotlin 2.4 对齐）
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)

            // Gate3-P1：播放内核独立模块。UI 签名透传引擎类型，必须 api 导出。
            api(project(":player"))
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
            // 阶段一②：Exo 超分走 Media3 GlEffect（effect 与 exoplayer 同版本）
            implementation(libs.media3.effect)
            // Gate3-P6：`libs.mpv.lib`（mpv-android）已随 Android mpv 内核一起移除
            // 引擎编译必需（:app 经 bundles.android.base 间接持有，此处显式声明）：
            // core-ktx（androidx.core.net.toUri）+ coroutines-android（Dispatchers.Main）
            implementation(libs.core.ktx)
            implementation(libs.coroutines.android)

            // Android Studio 渲染 commonMain 的 @Preview 需要渲染器（注解在 commonMain，
            // 渲染能力在平台源集）。桌面侧渲染要 org.jetbrains.compose.ui:ui-tooling-desktop，
            // 暂未引入——当前预览在 Android Studio 里看。
            implementation(libs.compose.ui.ui.tooling)

            // P6d-1-C：动态取色（Kyant0 m3color，无 KMP 坐标，仅 androidMain；坐标从 :app 照搬）
            implementation(libs.kyant.m3color)

            // G1-1A：SafFileManager 下沉 androidMain——DocumentFile
            implementation(libs.androidx.documentfile)
            // G1-1A：Networks.kt 下沉 androidMain——ListenableFuture.await（WorkManager 链路）
            implementation(libs.guava)
            // G1-1B：worker（HanimeDownloadManager/Worker）下沉 androidMain——WorkManager + LiveData
            implementation(libs.work.runtime.ktx)
            implementation(libs.lifecycle.livedata.core)
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
                // 主题重做 P2：m3color 提为 api——桌面的预生成工具（desktopApp 的
                // GenThemeBoards，走 HAN1ME_GEN_BOARDS=1 触发）直接用它跑色算；
                // 运行时只有 Android 跟随系统槽还需要它。
                api(libs.kyant.m3color)
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
            // Gate4-1：iOS 图片管线走 Ktor（Darwin + 通用头/ECH 配置，见 ImagePipeline）
            implementation(libs.coil.network.ktor3)
        }

        // 阶段 B：跨平台测试源集。
        // 此前**所有**测试都塞在 desktopTest，导致 Parser 这类 pure-Kotlin 逻辑
        // 在 iOS / Android 侧永远不被覆盖。commonTest 的代码会被编译进每个 target
        // 的 test compilation —— Windows 上仍通过 `:shared:desktopTest` 跑，
        // 但同一份用例在 iOS / Android 上也会执行。
        // 依赖要显式声明：commonMain 用的是 implementation，不传递给 test 源集。
        commonTest.dependencies {
            implementation(kotlin("test"))
            // 字节流 / 夹具读写走 okio（与 commonMain 的备份 I/O 同一套，不用 java.io）
            implementation(libs.okio)
            // GIF 录制等用例用到 runBlocking
            implementation(libs.coroutines.core)
        }

        // M7-4：iOS 播放引擎真实验证（iosSimulatorArm64Test 跑在模拟器上）
        val iosTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // 阶段一⑧：桌面端（JVM）单测——裁剪几何换算 / 文件名反解等纯逻辑，
        // Windows 上即可运行（iOS 测试要模拟器，Android 测试要设备）。
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Skia 原生库：decodeAvatarSource 走 skia Image，JVM 测试里
                // 没有 desktopApp 注入的 skiko-awt-runtime 就会
                // ExceptionInInitializerError（本地库未加载）。
                // 版本必须与 compose 带来的 skiko 对齐（0.150.1）。
                // 原生库按 OS 分发：CI 跑 Windows（见 ci.yml 注释），本地 Mac
                // 此前是靠 sonner-desktop 顺带把 macos-arm64 抬进来——
                // sonner 已删（Toast 走官方 M3），各 OS 在此显式声明，
                // 不用的 OS 的包只是躺在 classpath 里，不会被加载。
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.150.1")
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.150.1")
            }
        }
    }
}

// P4：composeRes 资源基建（公共 Res 类 + 固定包名；:app 的 R 与 shared composeRes 双轨，P6 收敛）
compose.resources {
    publicResClass = true
    packageOfResClass = "lovehan1me"
}

// ---------- 弹弹play 凭据：构建期注入（与 animeko 同一手法） ----------
// 值放在**根目录 local.properties**（已在 .gitignore 里），或 CI 用 -P 传；
// 生成一个常量文件喂给 commonMain。没配时生成空串，链路退回「未配置·去设置」，
// 于是：发行包/本地调试打开即用，而源码与 git 历史里永远没有密钥。
// 设置页依然可覆盖 —— 那是给自带 AppID 的用户留的，不再是必经步骤。
val danmakuLocalProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

fun danmakuCredential(name: String): String =
    danmakuLocalProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: ""

/** 只处理会改变字面量边界的三个字符；凭据本身是 `[A-Za-z0-9_-]`。 */
fun String.asKotlinStringLiteral(): String = buildString {
    append('"')
    for (ch in this@asKotlinStringLiteral) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            else -> append(ch)
        }
    }
    append('"')
}

val generateDanmakuCredentials = tasks.register("generateDanmakuCredentials") {
    // 转义在配置期做完：doLast 里若调用脚本顶层函数，会把 build script 对象一起序列化，
    // 配置缓存直接拒绝。任务动作里只留 String 与 Provider。
    val appIdLiteral = danmakuCredential("han1me.danmaku.dandan.app.id").asKotlinStringLiteral()
    val appSecretLiteral = danmakuCredential("han1me.danmaku.dandan.app.secret").asKotlinStringLiteral()
    val outputDir = layout.buildDirectory.dir("generated/danmakuCredentials/kotlin")

    inputs.property("appId", appIdLiteral)
    inputs.property("appSecret", appSecretLiteral)
    outputs.dir(outputDir)

    doLast {
        val packageDir = outputDir.get().asFile.resolve("lovehan1me/core/domain/model").apply { mkdirs() }
        packageDir.resolve("DanmakuBuildCredentials.kt").writeText(
            """
            |package lovehan1me.core.domain.model
            |
            |// 由 :shared:generateDanmakuCredentials 生成，勿手改。
            |// 值来自根目录 local.properties（gitignore）或 -P；缺省为空串 = 本构建未内置凭据。
            |// 官方对开源客户端的要求正是"源码里用占位符"，所以真实值只存在于构建产物。
            |// 放在 AppSettings 同包：core 不该为了读一个常量去依赖 data 层。
            |internal object DanmakuBuildCredentials {
            |    const val APP_ID: String = $appIdLiteral
            |    const val APP_SECRET: String = $appSecretLiteral
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateDanmakuCredentials)
}
