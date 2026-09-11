package lovehan1me.core.util

import java.io.File

// 放在用户目录下的稳定位置：避免每次启动重新解包 shader。
internal actual suspend fun mpvShaderTargetDir(): String? =
    File(System.getProperty("user.home"), ".lovehan1me/shaders").absolutePath
