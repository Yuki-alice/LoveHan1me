package lovehan1me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import lovehan1me.logic.SettingsRepository
import lovehan1me.data.datastore.DataStoreManager
import lovehan1me.ui.navigation.main.PlatformScreens
import lovehan1me.ui.screen.web.CloudflareVerificationWebView
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

/**
 * M1/M2 iOS 入口：Xcode 工程把返回的 UIViewController 设为 rootViewController。
 *
 * 启动链与桌面 Main.kt 对齐（DataStore 初始化 + Settings 装配）。
 * M2 起使用须知/来源确认门控进共享 App，此处不再自动置位。
 * `DataStoreManager` 的作用域是 `Dispatchers.Default`，此处主线程
 * `runBlocking` 不会与 `Dispatchers.Main` 死锁；try/catch 保证初始化失败也不炸启动屏
 *（首页走 loading + 重试）。
 *
 * 图片加载无需注册单例：`rememberHanimeImageLoader` 的 iosMain actual 自带默认构造。
 */
fun MainViewController(): UIViewController {
    runCatching {
        runBlocking {
            DataStoreManager.initialize()
            SettingsRepository.install(DataStoreManager)
        }
    }
    return ComposeUIViewController {
        App(
            // M5-5：CF 人机验证走系统 WebKit（WKWebView 直嵌），替代占位页
            platformScreens = PlatformScreens(
                cloudflare = { route ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(Res.string.complete_cloudflare_verification_with_warning),
                            modifier = Modifier.padding(12.dp),
                        )
                        CloudflareVerificationWebView(
                            url = route.url,
                            onVerificationPassed = { onBack() },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                },
            ),
        )
    }
}
