package io.github.daisukikaffuchino.han1meviewer

actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
