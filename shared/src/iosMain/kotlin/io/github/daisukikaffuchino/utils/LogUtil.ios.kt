package io.github.daisukikaffuchino.utils

import platform.Foundation.NSLog

// iOS：NSLog；异常栈用 stackTraceToString() 追加（Native 没有 printStackTrace 的等价输出通道）
internal actual fun platformLog(
    priority: Int,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val text = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
    NSLog("%@", "[$tag]$text")
}
