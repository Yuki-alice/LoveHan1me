package lovehan1me.core.util

import androidx.annotation.StringRes

/**
 * P6b：SonnerToast 整体下沉 :shared 后 commonMain 无法持有 `@StringRes Int` 重载。
 * :app 侧统一用本助手先把 R.string 解析为字符串再调用 Toast（行为与原 int 重载一致：
 * applicationContext.getString(resId, *formatArgs)），适用于 compose 与非 compose 上下文。
 */
fun toastText(@StringRes resId: Int, vararg formatArgs: Any): String =
    applicationContext.getString(resId, *formatArgs)
