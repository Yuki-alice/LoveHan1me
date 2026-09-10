package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext

// P6d-3-B：iOS 默认 ImageLoader（TODO P7 再特化，照抄 GetchuImageLoader.ios.kt）。
@Composable
actual fun rememberHanimeImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    return remember(context) {
        ImageLoader.Builder(context).build()
    }
}
