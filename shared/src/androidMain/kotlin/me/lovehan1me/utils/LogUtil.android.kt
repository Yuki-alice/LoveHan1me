package me.lovehan1me.utils

import android.util.Log

// Android：透传给 android.util.Log，异常栈按原实现（Log.w/e 的三参重载）拼在消息后
internal actual fun platformLog(
    priority: Int,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val text = if (throwable == null) message else "$message\n${Log.getStackTraceString(throwable)}"
    Log.println(priority, tag, text)
}
