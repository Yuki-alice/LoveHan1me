@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Clock
import java.time.Year
import java.time.ZoneId

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.compose.compiler)
    id("com.mikepenz.aboutlibraries.plugin") version "15.0.4"
    id("com.github.ben-manes.versions") version "0.59.0"
}

// SDK 版本统一从 gradle.properties 读（与 :shared 的约定插件一致，消除硬编码）。
// 平台统一到 android-37（compileSdk）；targetSdk 有意降到 36 = 退出 Android 17 行为变更，详见 gradle.properties。
val appCompileSdk = providers.gradleProperty("han1me.android.appCompileSdk").getOrElse("37").toInt()
val appTargetSdk = providers.gradleProperty("han1me.android.appTargetSdk").getOrElse("36").toInt()
val appMinSdk = providers.gradleProperty("han1me.android.appMinSdk").getOrElse("29").toInt()

// 原 buildSrc 的 `Config.thisYear`（UTC+8 时区的当前年，搜索年份上限）。
// buildSrc 只为这一组常量存在，却要在每次构建前先编译一遍、拖慢配置期
// ⇒ 就地计算，等价替换后删除 buildSrc。
val searchYearEnd = Year.now(Clock.system(ZoneId.of("UTC+8"))).value

android {
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "me.lovehan1me"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = 260805
        versionName = "26.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
        buildConfigField("int", "SEARCH_YEAR_RANGE_END", "$searchYearEnd")
    }

    // NDK/CMake 已移除：src/main/cpp（chino.cpp / kaffu.c）为死代码，全仓无 System.loadLibrary 引用；
    // 且 NDK 28.2 会在配置阶段触发数百 MB 下载，国内网络下长时间挂起，直接拖垮首次构建。

    splits {
        abi {
            isEnable = gradle.startParameter.taskRequests.toString().contains("Release")
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_new"
        }

        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_debug"
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        disable += setOf("EnsureInitializerMetadata")
    }
    namespace = "lovehan1me"

    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.value(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-jvm-default=enable"
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val apkName = "LoveHan1me-v${output.versionName.get()}.apk"
            (output as VariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    // KMP 共享模块：P1 起，无平台耦合的 model / state / exception 已下沉到 :shared
    implementation(project(":shared"))

    // P6d-1：R.drawable→Res.drawable 适配需 CMP resources（painterResource/DrawableResource/Res）
    implementation(compose.components.resources)

    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.datastore.preferences)

    implementation(libs.bundles.android.base)
    implementation(libs.bundles.android.jetpack)

    implementation(platform(libs.compose.compose.bom))
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.ui)
    debugImplementation(libs.compose.ui.ui.tooling)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.jsoup)

    // P3：网络层已迁至 :shared（ServiceCreator/拦截器链 → jvmMain，5 个 Service → commonMain Ktor）。
    // :app 移除 retrofit/converter-serialization/okhttp-dns-over-https。
    //
    // ⚠️ okhttp **不是残留、必须保留**：HanimeDownloadWorker 的断点续传下载链路直接使用
    // okhttp3.Request/Response/ResponseBody（Range 请求、续传、closeQuietly），改用 Ktor
    // 是另一个量级的改造。okhttp-dns-over-https 确实已不需要（HanimeDns 已下沉 shared jvmMain）。
    // :app 侧代码引用 Ktor HttpResponse/bodyAsText 等类型，补 ktor-client-core。
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.mpv.lib)

    ksp(libs.room.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.test.junit)
}
