package lovehan1me.app.navigation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lovehan1me.feature.settings.HomeSettingsPage

// M2：iOS 降级占位（实现见 jvmMain；桌面与 Android 走真实现）。
// Home 设置页依赖 BackupManager（jvmMain），网络页直引 OkHttp 链——
// 两者皆无 iOS 语义，P7 再定 iOS 设置形态。
@Composable
actual fun HomeSettingsRouteScreen(
    page: HomeSettingsPage,
    onNavigateToHKeyframes: () -> Unit,
    onNavigateToSharedHKeyframes: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    downloadSettingsContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Settings (${page.name})",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "iOS settings UI ships in P7",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
actual fun NetworkSettingsRouteScreen(embedded: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Network settings",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Custom DNS / proxy is not available on iOS",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
