package io.github.daisukikaffuchino.utils

import android.content.pm.ApplicationInfo
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext

// Android：读应用 ApplicationInfo 的 debuggable 标记（与 :app BuildConfig.DEBUG 同源）。
actual fun isDebugBuild(): Boolean {
    val flags = Han1meDatabaseContext.appContext.applicationInfo.flags
    return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
