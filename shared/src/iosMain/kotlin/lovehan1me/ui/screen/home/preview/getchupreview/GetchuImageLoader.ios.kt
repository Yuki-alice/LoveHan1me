package lovehan1me.ui.screen.home.preview.getchupreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext

// P6d-2：iOS 默认 ImageLoader（TODO P7 getchu 域名特化）。
@Composable
actual fun rememberGetchuImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    return remember(context) {
        ImageLoader.Builder(context).build()
    }
}
