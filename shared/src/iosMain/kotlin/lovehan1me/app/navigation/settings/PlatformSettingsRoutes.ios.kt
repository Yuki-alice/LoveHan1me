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

// 阶段一④：Home 设置页已整体搬入 commonMain，iOS 复用同一套（含备份导出/导入，
// 经 UIDocumentPicker 走“写临时文件再导出”）；本文件只剩网络页占位。
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
