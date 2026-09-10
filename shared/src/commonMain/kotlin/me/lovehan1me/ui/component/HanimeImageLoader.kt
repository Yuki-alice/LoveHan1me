package me.lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage

/**
 * P6d-3-B：通用图片加载器（解锁 16 个 `AsyncImage` 文件）。
 *
 * 形状照抄 P6d-2 的 `rememberGetchuImageLoader`（commonMain expect + jvmMain 真实现 + iosMain 默认）：
 * jvmMain 复用 `createGetchuImageLoader` 的 OkHttp + HDns + 代理构造，只是去掉 getchu 域名特化。
 */
@Composable
expect fun rememberHanimeImageLoader(): ImageLoader

/**
 * 通用图片（`AsyncImage` 薄包装，默认挂通用 loader）。
 *
 * 后续 16 个文件的迁移即把 `AsyncImage(` 改成 `HanimeAsyncImage(` 并删掉显式 `imageLoader` 实参
 * （已显式传 loader 的调用点保持不动）。
 */
@Composable
fun HanimeAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = null,
    alignment: Alignment = Alignment.Center,
    imageLoader: ImageLoader = rememberHanimeImageLoader(),
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        fallback = fallback,
        alignment = alignment,
        contentScale = contentScale,
    )
}
