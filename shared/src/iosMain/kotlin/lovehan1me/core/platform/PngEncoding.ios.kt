package lovehan1me.core.platform

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * iOS：走本项目**已经在用**的 skiko（`AvatarImageIo.ios.kt` 的裁剪与 PNG 落盘就是这条路），
 * 而不是 `UIImagePNGRepresentation`。
 *
 * 为什么不用 `UIImage`：从裸像素数组造 `UIImage` 要先绕 `CGImage` + `CGBitmapContext`
 * （还要处理 `bytesPerRow`、位图信息掩码、色彩空间），符号形态比 skiko 多得多；
 * 而 skiko 这条路径在 CI 的 macOS runner 上已被编译验证过（见 `AvatarImageIo.ios.kt`）。
 *
 * ## 像素字节序：刻意**不用** `ImageInfo.makeN32`
 * `kN32` 是**平台相关**别名（不同平台上可能是 RGBA、也可能是 BGRA）。一旦猜错就是
 * "红蓝通道互换"—— 这种图编得出来、打得开、CI 也编译通过，只能靠肉眼在真机上发现。
 * 所以这里显式按 **RGBA_8888** 铺字节：自己按 R,G,B,A 写，`ImageInfo` 里也显式声明
 * `ColorType.RGBA_8888`，与平台的字节序假设彻底解耦。
 *
 * ## ⚠️ 本文件**未经编译验证**（Windows 无法编译 Kotlin/Native，见项目约定）
 * 已做的离线验证：把 `skiko-iosarm64-0.150.1.klib` 拉下来解包，
 * 在 `default/linkdata/package_org.jetbrains.skia/*.knm` 里确认了
 * `makeRaster` / `installPixels` / `makeFromBitmap` / `encodeToData` /
 * `RGBA_8888` / `UNPREMUL` / `ImageInfo` **在 iosArm64 这一侧确实存在**
 * （脚本 `.workbuddy/_klib_probe.py`）。这能排除"符号不存在"，但**排除不了签名形态的差异**。
 *
 * 首次 CI 编译最可能报错的两处，与备用改法：
 * 1. `ImageInfo(width, height, colorType, alphaType)` —— 参数顺序是**宽、高、色型、alpha 型**
 *    （已在 skiko 0.150.1 的 JVM jar 上用 `javap` 核对：
 *    `ImageInfo(int,int,ColorType,ColorAlphaType)`）。
 *    若报签名不符，备用形态 `ImageInfo.makeN32Premul(width, height)`（klib 里也有）
 *    —— 但那样就回到上面那条字节序风险，需同时把字节序改成 BGRA。
 * 2. `Image.makeRaster(info, bytes, rowBytes)` 的第三参**没有默认值**
 *    （jar 里没有 `makeRaster$default`），必须显式传 `width * 4`。
 *    备用改法：`Bitmap().apply { installPixels(info, bytes, width * 4) }` 后
 *    `Image.makeFromBitmap(bitmap)` —— 后者已在 `AvatarImageIo.ios.kt` 里被验证过。
 */
actual fun encodePngArgb(pixels: IntArray, width: Int, height: Int): ByteArray? {
    if (width <= 0 || height <= 0 || pixels.size != width * height) return null
    return runCatching {
        // ARGB(Int) → RGBA(每像素 4 字节)
        val rgba = ByteArray(width * height * 4)
        var out = 0
        for (pixel in pixels) {
            rgba[out++] = ((pixel ushr 16) and 0xFF).toByte() // R
            rgba[out++] = ((pixel ushr 8) and 0xFF).toByte() // G
            rgba[out++] = (pixel and 0xFF).toByte() // B
            rgba[out++] = ((pixel ushr 24) and 0xFF).toByte() // A
        }

        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val image = Image.makeRaster(info, rgba, width * 4)
        // encodeToData 返回可空；且 iOS 侧 skiko 的 Data.getBytes 没有无参重载，
        // 必须显式给 (offset, length) —— 与 AvatarImageIo.ios.kt 同一写法
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: return@runCatching null
        data.getBytes(0, data.size)
    }.getOrNull()
}
