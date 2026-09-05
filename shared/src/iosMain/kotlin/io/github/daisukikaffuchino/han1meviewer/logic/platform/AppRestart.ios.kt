package io.github.daisukikaffuchino.han1meviewer.logic.platform

// P6d-4：iOS 无法自杀重启进程，降级 no-op（设置项在 iOS 侧由壳层裁剪，P7 收口）
actual fun restartApp(killProcess: Boolean) {
}
