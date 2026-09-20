package lovehan1me.core.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build

/**
 * G1-1A：由 :app 下沉 shared/androidMain。
 * 沿用 [lovehan1me.data.database.dao.Han1meDatabaseContext.setContext] 的注入模式：
 * 属性只读，赋值走 [setApplicationContext]，:app 的 Application 入口调用即可，
 * 从而保持 shared 不反向依赖 :app。
 */
lateinit var applicationContext: Context
    private set

fun setApplicationContext(context: Context) {
    applicationContext = context
}

val application: Application
    get() = applicationContext as Application

val isX86_64Device: Boolean
    get() = Build.SUPPORTED_ABIS.any { it == "x86_64" }

val Context.activity: Activity?
    get() {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
