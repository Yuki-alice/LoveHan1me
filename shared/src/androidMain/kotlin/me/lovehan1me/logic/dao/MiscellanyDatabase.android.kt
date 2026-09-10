package me.lovehan1me.logic.dao

import android.content.Context
import androidx.room.Room

/**
 * Android 端 Context 注入点：Android 的 Room.databaseBuilder 强制要求 Context。
 * :app 首次使用 [MiscellanyDatabase] 前调用 [setContext] 注入即可（见 app 侧 instance 扩展）。
 */
object Han1meDatabaseContext {
    @Volatile
    private var context: Context? = null

    fun setContext(appContext: Context) {
        context = appContext
    }

    internal val appContext: Context
        get() = requireNotNull(context) {
            "Han1meDatabaseContext 尚未初始化：请先调用 setContext()"
        }
}

// Android：走系统 SQLite（Context overload，无 setDriver），文件路径由调用方提供
actual fun createMiscellanyDatabase(filePath: String): MiscellanyDatabase =
    Room.databaseBuilder<MiscellanyDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    ).build()
