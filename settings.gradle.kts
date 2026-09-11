pluginManagement {
    // convention plugin 所在的独立构建，须先于 repositories 声明
    includeBuild("build-logic")
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阶段一②：mediamp 的平台运行时库（mediamp-mpv-runtime-{windows,linux,macos}-*）
        // 在阿里云镜像**长期 404**，而 Gradle 的仓库亲和性会让"从 aliyun 拿到元数据"的
        // 模块只去 aliyun 找 jar（不会回落到后面的源）→ :desktopApp:run 直接失败。
        // 整组 mediamp 强制走 Maven Central 官方源，绕开镜像缺口。
        // （其余依赖仍优先走国内镜像，见下方顺序。）
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("org.openani.mediamp") }
        }
        // 同类问题：所有 com.github.* 组都来自 JitPack，镜像（aliyun/腾讯云）代理它们时
        // 元数据能拿到、jar 常缺 → `:app:assembleDebug` 的 transform 环节直接失败。
        // 整组强制回 JitPack 源，保证元数据与产物出自同一仓库。
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io/") } }
            filter { includeGroupByRegex("com\\.github\\..*") }
        }
        // 同类问题（③）：coil3 的 KMP 产物在阿里云镜像下"元数据拿得到、klib 取不到"。
        // 阿里云先命中 coil3 的 .module → 亲和性把它钉死在 aliyun → 请求
        // coil-network-{core,ktor3}-iosArm64Main-3.6.1.klib 时 404
        // （仓库里只有全小写的 coil-network-*-iosarm64-3.6.1.klib）。
        //
        // ⚠️ 该坑只在 **iOS 目标**暴露，而 iOS 只能由 macOS 宿主构建 ——
        //    Windows 侧加了阿里云镜像后，桌面/Android 全绿，唯独 iOS 编译从未跑过，
        //    所以一路没被发现。把 coil3 整组钉回 Maven Central，保证元数据与产物同源。
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("io.coil-kt.coil3") }
        }
        // 国内镜像优先：google()/mavenCentral() 直连在国内极慢，镜像命中可大幅缩短首次构建
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 官方源兜底（镜像缺包时回落）
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io/") }
        // M5-5：KCEF/JCEF 的 JOGL（gluegen-rt/jogl-all）只发布在 JogAmp 官方仓库。
        // 必须置于最后：该站国内基本不可达，前置会让依赖解析长时间挂起。
        maven { url = uri("https://jogamp.org/deployment/maven/") }
    }
}
rootProject.name = "LoveHan1me"

// 原 Android 单平台应用模块，迁移期间保持可编译，全部页面下沉到 :shared 后再移除
include(":app")

// KMP 共享模块与新增平台入口
include(":shared")
include(":desktopApp")
