package lovehan1me.core.platform

import androidx.compose.ui.graphics.ImageBitmap

// 阶段一⑧：iOS 的解码/编码需要 UIKit + CoreGraphics 的 cinterop 实现，
// 在 Mac 上验证前先返回 null —— 共享裁剪页会显示"平台暂不支持"而不是崩溃。
actual suspend fun decodeAvatarSource(source: String, maxPx: Int): ImageBitmap? = null

actual suspend fun cropAndSaveAvatar(
    source: String,
    rect: AvatarCropRect,
    outputPx: Int,
): String? = null
