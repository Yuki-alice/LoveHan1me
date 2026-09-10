package lovehan1me.core.platform

actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
