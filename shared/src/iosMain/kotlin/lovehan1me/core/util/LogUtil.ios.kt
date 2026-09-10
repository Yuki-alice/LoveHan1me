package lovehan1me.core.util

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create

// iOS：NSLog；异常栈用 stackTraceToString() 追加（Native 没有 printStackTrace 的等价输出通道）
internal actual fun platformLog(
    priority: Int,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val text = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
    // 必须显式转成 NSString 再传入：NSLog 是 variadic 函数，K/N 对 variadic 参数
    // 不会自动做 String→NSString 桥接，直接传 Kotlin String 会被 ObjC 端当作坏指针导致 SIGSEGV。
    NSLog("%@", NSString.create(string = "[$tag]$text"))
}
