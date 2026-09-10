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
