package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import lovehan1me.feature.settings.HomeSettingsPage

/**
 * M2：JVM 专属设置屏的跨平台入口。
 *
 * - 阶段一④：`HomeSettingsRouteScreen` 已整体搬入 commonMain（三端同一套；
 *   伪装图标选择器由 JVM `Dialog` 改为 Material3 `AlertDialog`），不再是 expect；
 * - `NetworkSettingsRouteScreen` 的实现仍留在 `jvmMain`
 *  （直引 OkHttp 的 `HDns` / `HProxySelector` / `ServiceCreator`，
 *   无 iOS 对应物），以 `actual` 形式提供；
 * - iOS 网络页以降级占位实现（网络代理/DoH 本就无 iOS 语义，P7 再定；调用方签名零差异）。
 */
@Composable
expect fun NetworkSettingsRouteScreen(embedded: Boolean = false)
