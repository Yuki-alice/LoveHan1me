package lovehan1me.core.platform

/**
 * ARGB 像素 → PNG 字节流（M3-c「截图 + 系统分享」的平台编码入口）。
 *
 * ## 为什么不延续 M3-b 的"纯 Kotlin 编码器"路线
 * GIF 用的是自研 `Gif89aEncoder`：它的 LZW 复杂度可控，还能在单测里用独立解码器逐像素往返验证。
 * PNG 不同 —— 它必须过 **zlib/deflate** 那一层：纯 Kotlin 要么自己实现 deflate，
 * 要么只能写"存储块"（不压缩），后者会把 1080p 截图的体积放大到十几 MB。
 * 三端本来就各有一个成熟实现（Android `Bitmap.compress` / JVM `ImageIO` /
 * iOS 侧本项目已在用的 skiko），所以这里只做一层薄封装，把差异关进 actual。
 *
 * ## 与 [exportMediaAndShare] 的分工
 * 本函数**只产出字节**，落盘与系统分享由 `MediaExport` 负责 ——
 * 与 M3-b 的 `GifRecorder`（编码）→ `GifCaptureDialog`（导出）是同一分工。
 *
 * ## 线程
 * 非挂起、且**不切线程**：调用方决定在哪跑。`ScreenshotCapturer` 会把这一步放到
 * `Dispatchers.Default` —— 1080p 的 PNG 压缩有几十到几百毫秒，不该压在 UI 线程上。
 *
 * @param pixels 长度必须等于 `width * height`，元素为 `0xAARRGGBB`
 * @return PNG 字节流；尺寸与像素数不符、或平台编码失败时返回 `null`。
 *         **不抛异常** —— 与 `PlaybackEngine.grabFrameArgb` 的失败约定一致：由调用方提示用户。
 */
expect fun encodePngArgb(pixels: IntArray, width: Int, height: Int): ByteArray?
