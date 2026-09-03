@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.parcelize)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.compose.compiler)
    id("com.mikepenz.aboutlibraries.plugin") version "15.0.4"
    id("com.github.ben-manes.versions") version "0.59.0"
}

android {
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.daisukikaffuchino.han1meviewer"
        minSdk = 29
        targetSdk = 37
        versionCode = 260805
        versionName = "26.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
        buildConfigField("int", "SEARCH_YEAR_RANGE_END", "${Config.thisYear}")

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                abiFilters += "arm64-v8a"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

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
    namespace = "io.github.daisukikaffuchino.han1meviewer"

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
            val apkName = "Han1meViewer-v${output.versionName.get()}.apk"
            (output as VariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    // KMP 共享模块：P1 起，无平台耦合的 model / state / exception 已下沉到 :shared
    implementation(project(":shared"))

    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.glance.appwidget)
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
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.compose.avatar.cropper)
    implementation(libs.kyant.m3color)
    implementation(libs.sonner)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.jsoup)

    // P3：网络层已迁至 :shared（ServiceCreator/拦截器链 → jvmMain，5 个 Service → commonMain Ktor）。
    // :app 移除 retrofit/converter-serialization/okhttp-dns-over-https；仍保留 okhttp（worker/settings/Coil 直接使用）。
    // :app 侧代码引用 Ktor HttpResponse/bodyAsText 等类型，补 ktor-client-core。
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)

    implementation(libs.coil)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.cast)
    implementation(libs.mpv.lib)

    ksp(libs.room.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.test.junit)
}
