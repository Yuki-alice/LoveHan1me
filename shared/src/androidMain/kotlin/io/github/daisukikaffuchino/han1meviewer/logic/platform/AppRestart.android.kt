package io.github.daisukikaffuchino.han1meviewer.logic.platform

import android.content.Intent
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext
import kotlin.system.exitProcess

actual fun restartApp(killProcess: Boolean) {
    // 原 utils 库 ActivityManager.restart 逻辑照搬（applicationContext → 共享 holder）
    val context = Han1meDatabaseContext.appContext
    context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
        ?.let(context::startActivity)
    if (killProcess) exitProcess(0)
}
