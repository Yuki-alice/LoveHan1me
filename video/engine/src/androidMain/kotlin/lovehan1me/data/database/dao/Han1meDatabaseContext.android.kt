package lovehan1me.data.database.dao

import android.content.Context

/**
 * Android 端 Context 注入点：Android 的 Room.databaseBuilder 强制要求 Context。
 *
 * 独立成文件的原因：它原先与 `MiscellanyDatabase` 同处一个文件，在 M3-a 删除 H 帧时
 * 被连带误删（当时 15 个 androidMain 文件依赖它，直接导致 Android 端编译失败）。
 * 全局 Context 持有者不属于任何单一数据库，必须独立存在，
 * 以免日后按「功能」批量删文件时再次被误伤。
 *
 * :app 在 Application 入口调用 [setContext] 注入即可。
 */
object Han1meDatabaseContext {
    @Volatile
    private var context: Context? = null

    fun setContext(appContext: Context) {
        context = appContext
    }

    val appContext: Context
        get() = requireNotNull(context) {
            "Han1meDatabaseContext 尚未初始化：请先调用 setContext()"
        }
}
