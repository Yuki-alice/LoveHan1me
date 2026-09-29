package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.login.ManualInputCookiesScreen
import kotlinx.coroutines.launch

/**
 * 手动粘贴 cookie 登录：写入凭据后回调成功。
 *
 * `login(cookie)`（jvmMain `HanimeAccount.kt`，iOS 不可见）改为内联
 * `SettingsRepository.update`（两者语义一致）。cookie 由 HCookieJar 每请求读取，
 * 不需要重建网络。
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
                onLoginSucceeded()
            }
        },
    )
}
