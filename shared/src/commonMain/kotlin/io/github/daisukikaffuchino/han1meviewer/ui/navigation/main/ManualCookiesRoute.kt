package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.network.HanimeNetwork
import io.github.daisukikaffuchino.han1meviewer.ui.screen.login.ManualInputCookiesScreen
import kotlinx.coroutines.launch

/**
 * M2：自 `:app` `AuthRouteScreens.kt` 下沉（同名，`:app` 侧删除）。
 *
 * 唯一差异：`login(cookie)`（jvmMain `HanimeAccount.kt`，iOS 不可见）改内联
 * `SettingsRepository.update`（两者语义逐行一致）+ `HanimeNetwork.rebuildNetwork()`。
 * 后者在 JVM 重建 OkHttp 传输层 + 各 service，iOS 侧 no-op 后重建 service。
 */
@Composable
fun ManualCookiesRouteScreen(
    onBack: () -> Unit,
    onLoginSucceeded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ManualInputCookiesScreen(
        onBack = onBack,
        onCookieScanned = { cookie ->
            scope.launch {
                SettingsRepository.update {
                    it.copy(isAlreadyLogin = true, loginCookie = cookie)
                }
                HanimeNetwork.rebuildNetwork()
                onLoginSucceeded()
            }
        },
    )
}
