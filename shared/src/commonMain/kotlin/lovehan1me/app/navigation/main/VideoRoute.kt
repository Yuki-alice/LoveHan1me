package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import lovehan1me.app.bridge.NoopVideoPageHost
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.feature.video.EnqueueDownloadRequest
import lovehan1me.feature.video.VideoRouteHostScreen

/**
 * M3：自 `:app` 下沉（同名，`:app` 侧删除）。
 *
 * 与 `:app` 版的差异：`activity: MainActivity` 改注入——[platformHost]（窗口操作）、
 * [onRegisterPageHost]（Android 侧注册给 MainActivity 的 PiP 广播）与导航/下载回调。
 */
@Composable
fun VideoRouteScreen(
    route: VideoRoute,
    platformHost: VideoPageHost = NoopVideoPageHost,
    onRegisterPageHost: ((VideoPageHost?) -> Unit)? = null,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenSearchRoute: (SearchRoute) -> Unit,
    onEnqueueDownload: (EnqueueDownloadRequest) -> Unit,
) {
    VideoRouteHostScreen(
        route = route,
        platformHost = platformHost,
        onRegisterPageHost = onRegisterPageHost,
        onBack = onBack,
        onNavigateHome = onNavigateHome,
        onNavigateToVideo = onNavigateToVideo,
        onOpenSearchRoute = onOpenSearchRoute,
        onEnqueueDownload = onEnqueueDownload,
    )
}
