package lovehan1me.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.HanimeTheme
import lovehan1me.core.util.AppToast

/**
 * P6c-D：桌面最小骨架屏（desktopApp 内，非 shared）。
 *
 * 目的：验证 P6b 下沉的 theme/组件/AppToast 的 commonMain 化在桌面端真实可渲染
 * （无 ClassNotFound / 链接错误）。
 *
 * P6d-1-C：改用 shared HanimeTheme 真跑验证（桌面动态取色回退固定色板，见 DynamicSchemeProvider）。
 */
@Composable
fun DesktopScaffold() {
    HanimeTheme {
        AppToast.Host()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HanimeDefaults.Colors.pageSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Desktop skeleton ready",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "LoveHan1me 0.1.0 (KMP shared commonMain theme/component/toast)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { AppToast.success("Desktop toast: shared AppToast OK") },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Toast 冒烟（shared AppToast）")
            }
        }
    }
}
