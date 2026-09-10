package lovehan1me.utils

// Desktop(JVM)：标准输出 + 异常栈
internal actual fun platformLog(
    priority: Int,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    println("[$tag]$message")
    throwable?.printStackTrace()
}
