package io.github.daisukikaffuchino.utils

import androidx.compose.ui.platform.ClipEntry

// P6d-4：iOS 剪贴板暂降级 no-op（复制无效果，UI 有 toast 反馈）；真实现随 P7 平台能力收口
actual fun createTextClipEntry(text: String): ClipEntry? = null
