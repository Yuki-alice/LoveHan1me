import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    // P6c-D：骨架屏用 shared 同款 CMP material3（与 :shared 版本一致，含 Expressive opt-in）
    implementation(libs.cmp.material3)

    // P3a 验证切片：图片（Coil 3 桌面，网络引擎 ktor3）与 HTML 解析
    implementation(libs.coil.compose.core)
    implementation(libs.coil.network.ktor3)
    // coil-compose-core 的 AsyncImage 必须显式传 imageLoader；带默认 imageLoader / 单例注册的
    // API 在 coil-compose 完整构件里（desktop 需 setSingletonImageLoaderFactory 才能用网络组件）
    implementation("io.coil-kt.coil3:coil-compose:3.6.1")
    // P4：ksoup 统一走版本目录（P3a 曾硬编码同坐标）
    implementation(libs.ksoup)
}

compose.desktop {
    application {
        mainClass = "io.github.daisukikaffuchino.han1meviewer.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Han1meViewer"
            packageVersion = "0.1.0"
        }
    }
}
