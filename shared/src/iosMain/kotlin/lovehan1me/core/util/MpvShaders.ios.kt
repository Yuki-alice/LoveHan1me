package lovehan1me.core.util

// 阶段一②：iOS 不做超分（AVPlayer 原生管线不支持挂自定义 shader）。
internal actual suspend fun materializeMpvShaders(level: Int): String? = null

internal actual suspend fun mpvShaderTargetDir(): String? = null
