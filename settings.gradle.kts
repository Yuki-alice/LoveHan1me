pluginManagement {
    // convention plugin 所在的独立构建，须先于 repositories 声明
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io/") }
        // M5-5：KCEF/JCEF 的 JOGL（gluegen-rt/jogl-all）只发布在 JogAmp 官方仓库
        maven { url = uri("https://jogamp.org/deployment/maven/") }
    }
}
rootProject.name = "Han1meViewer"

// 原 Android 单平台应用模块，迁移期间保持可编译，全部页面下沉到 :shared 后再移除
include(":app")

// KMP 共享模块与新增平台入口
include(":shared")
include(":desktopApp")
