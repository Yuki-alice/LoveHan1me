package lovehan1me.logic.platform

/**
 * P6d-4：应用重启（原 :app 经 utils 库 ActivityManager.restart，Android-only）。
 * 触发点：切换域名 / 代理 / DoH 等需要进程重建才生效的设置。
 */
expect fun restartApp(killProcess: Boolean)
