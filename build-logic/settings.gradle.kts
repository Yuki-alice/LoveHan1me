// build-logic 独立构建：只服务于 convention plugin 的编译
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // 复用主工程的版本目录，保证插件编译期类型与运行时插件版本一致
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
