package lovehan1me.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * 桌面（JVM/Skiko）：`coil3.Image` → Skia `Bitmap` → Compose [ImageBitmap]。
 *
 * 与 iOS 实现同构（两端都基于 Skiko），差异只在 `toBitmap()` 背后的位图类型，
 * 由 Coil 的 nonAndroid 变体统一给出。
 */
internal actual fun Image.toImageBitmapOrNull(): ImageBitmap? = runCatching {
    SkiaImage.makeFromBitmap(toBitmap()).toComposeImageBitmap()
}.getOrNull()
