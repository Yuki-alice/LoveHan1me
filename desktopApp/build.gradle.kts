import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":shared"))
    // M5-5：PlatformScreens 槽位 lambda 引用 CloudflareRoute（NavKey 子类型），需运行时可见
    implementation(libs.navigation3.runtime.cmp)
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
        mainClass = "lovehan1me.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Han1meViewer"
            // macOS 的 jpackage 拒绝首位为 0 的版本号（原值 "0.1.0" 报
            // 「app-version 中的第一个数字不能为零或负数」→ createDistributable 直接失败）。
            // 该值同时用于 dmg 文件名与 CFBundleShortVersionString，与代码版本无关。
            packageVersion = "1.0.0"
            // 打包产物用 jlink 裁剪运行时，只带被引用的模块。DataStore 的 protobuf
            // 反射实现依赖 sun.misc.Unsafe，而它属于 jdk.unsupported，不显式带上就会在
            // 启动读 DataStore 时抛 NoClassDefFoundError: sun/misc/Unsafe 并整窗崩掉。
            // 注意：`:desktopApp:run` 跑在完整 JDK 上，看不出这个问题——只有打包产物才会暴露。
            modules("jdk.unsupported")
        }
    }
}
