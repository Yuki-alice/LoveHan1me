plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    // convention plugin 编译期需要的插件类型。
    // compileOnly：运行时由宿主工程 plugins {} 声明的真实插件版本提供，避免 classpath 冲突。
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}
