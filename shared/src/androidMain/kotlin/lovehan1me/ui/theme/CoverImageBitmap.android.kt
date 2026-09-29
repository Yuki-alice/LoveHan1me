package lovehan1me.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap

/**
 * Android：`coil3.Image` → `android.graphics.Bitmap` → Compose [ImageBitmap]。
 *
 * 硬件位图（`Config.HARDWARE`）不能在 CPU 侧读像素，先拷成软件位图再返回 —— 取色只读，
 * 不回流进图片管线，所以这次拷贝没有额外代价。
 */
internal actual fun Image.toImageBitmapOrNull(): ImageBitmap? = runCatching {
    toBitmap().let { raw ->
        val readable = if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            raw
        }
        readable?.asImageBitmap()
    }
}.getOrNull()
