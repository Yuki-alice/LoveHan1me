package io.github.daisukikaffuchino.han1meviewer.logic.platform

import kotlin.system.exitProcess

// P6d-4：桌面端重启语义 = 退出进程（设置页会先弹确认框），用户重开即新配置生效
actual fun restartApp(killProcess: Boolean) {
    if (killProcess) exitProcess(0)
}
