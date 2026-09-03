package io.github.daisukikaffuchino.han1meviewer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults
import io.github.daisukikaffuchino.utils.SonnerToast

/**
 * P6c-D：桌面最小骨架屏（desktopApp 内，非 shared）。
 *
 * 目的：验证 P6b 下沉的 theme/组件/SonnerToast 的 commonMain 化在桌面端真实可渲染
 * （无 ClassNotFound / 链接错误）。P3aVerificationScreen 保留，真站回归仍需用。
 *
 * 说明：真实 HanimeTheme（:app Theme.kt）依赖 Kyant0 m3color（Android-only），桌面暂以
 * Material3 亮/暗 scheme + shared HanimeDefaults 呈现；完整桌面主题化待 P6d/P7。
 */
@Composable
fun DesktopScaffold() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        SonnerToast.Host()
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
                text = "Han1meViewer 0.1.0 (KMP shared commonMain theme/component/toast)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { SonnerToast.success("Desktop toast: shared SonnerToast OK") },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Toast 冒烟（shared SonnerToast）")
            }
        }
    }
}
