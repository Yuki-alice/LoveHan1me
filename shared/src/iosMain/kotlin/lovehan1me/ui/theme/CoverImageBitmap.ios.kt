package lovehan1me.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * iOS（Skiko）：`coil3.Image` → Skia `Bitmap` → Compose [ImageBitmap]。
 *
 * 与桌面实现同构（两端都基于 Skiko）。注意 Skiko 在 iOS 与 JVM/awt 上的 API 并不完全
 * 一致（见 `AvatarImageIo.ios.kt` 的说明），改动这里必须真机/SDK 编译验证，桌面全绿
 * 不能替代 iOS 编译。
 */
internal actual fun Image.toImageBitmapOrNull(): ImageBitmap? = runCatching {
    SkiaImage.makeFromBitmap(toBitmap()).toComposeImageBitmap()
}.getOrNull()
