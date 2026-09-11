package lovehan1me.core.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import lovehan1me.data.database.dao.Han1meDatabaseContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

private fun openBitmap(source: String): Bitmap? {
    val context = Han1meDatabaseContext.appContext
    val uri = source.toUri()
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}

actual suspend fun decodeAvatarSource(source: String, maxPx: Int): ImageBitmap? =
    runCatching {
        val raw = openBitmap(source) ?: return null
        val scale = (maxPx.toFloat() / max(raw.width, raw.height)).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            raw
        }
        scaled.copy(Bitmap.Config.ARGB_8888, true).asImageBitmap()
    }.getOrNull()

actual suspend fun cropAndSaveAvatar(
    source: String,
    rect: AvatarCropRect,
    outputPx: Int,
): String? = runCatching {
    val raw = openBitmap(source) ?: return null
    val x = rect.x.coerceIn(0, max(0, raw.width - 1))
    val y = rect.y.coerceIn(0, max(0, raw.height - 1))
    val size = minOf(rect.size, raw.width - x, raw.height - y).coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(raw, x, y, size, size)
    val scaled = Bitmap.createScaledBitmap(cropped, outputPx, outputPx, true)

    val dir = File(Han1meDatabaseContext.appContext.cacheDir, "avatar").apply { mkdirs() }
    val out = File(dir, "avatar_${System.currentTimeMillis()}.png")
    FileOutputStream(out).use { fos ->
        scaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
    }
    out.absolutePath
}.getOrNull()
