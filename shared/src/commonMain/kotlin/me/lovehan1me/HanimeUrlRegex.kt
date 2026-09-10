package me.lovehan1me

/**
 * P4：自 :app HanimeManager.kt 下沉（Parser / HanimeVideo 依赖闭包使用，包名不变）。
 * HanimeManager.kt 其余顶层函数（链接拼装/登录登出等）保留 :app。
 */
val videoUrlRegex = Regex(
    """(?:(?:https?:)?//[^\s"'<>/]+|(?:hanime(?:1|one)|javchu)\.(?:com|me))?(?:/[^/?#\s"'<>]+)*/watch\?(?:[^#\s"'<>]*&)?v=(\d+)"""
)

fun String.toVideoCode() = videoUrlRegex.find(this)?.groupValues?.get(1)
