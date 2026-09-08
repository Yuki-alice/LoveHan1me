package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.DailyCheckInScreen

// M2：自 `:app` 下沉（去 MainActivity 依赖；全屏 window-mode 一并移除见 Screen）。
@Composable
fun DailyCheckInRouteScreen(
    onBack: () -> Unit,
) {
    DailyCheckInScreen(
        onBack = onBack,
    )
}
