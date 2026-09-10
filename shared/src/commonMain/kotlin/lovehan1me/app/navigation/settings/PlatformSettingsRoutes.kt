package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import lovehan1me.feature.settings.HomeSettingsPage

/**
 * M2：两个 JVM 专属设置屏的跨平台入口。
 *
 * - `HomeSettingsRouteScreen` / `NetworkSettingsRouteScreen` 的实现留在 `jvmMain`
 *  （直引 OkHttp 的 `HDns` / `HProxySelector` / `ServiceCreator` 与 `BackupManager`，
 *   无 iOS 对应物），以 `actual` 形式提供；
 * - iOS 侧以降级占位实现（网络代理/DoH 本就无 iOS 语义，P7 再定；调用方签名零差异）。
 */
@Composable
expect fun HomeSettingsRouteScreen(
    page: HomeSettingsPage,
    onNavigateToHKeyframes: () -> Unit = {},
    onNavigateToSharedHKeyframes: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    downloadSettingsContent: @Composable () -> Unit = {},
)

@Composable
expect fun NetworkSettingsRouteScreen(embedded: Boolean = false)
