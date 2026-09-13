package lovehan1me.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import lovehan1me.app.web.CloudflareVerificationWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import lovehan1me.app.navigation.main.PlatformScreens
import lovehan1me.feature.player.DesktopVideoPageHost
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import lovehan1me.app.App
import lovehan1me.desktop.runSmokeIfRequested
import lovehan1me.app.crash.installCrashHandler
import lovehan1me.data.SettingsRepository
import lovehan1me.data.datastore.DataStoreManager
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.feature.player.DesktopWindowHolder
import lovehan1me.core.util.LogUtil
import kotlinx.coroutines.runBlocking
import java.net.URI

/**
 * M1 桌面入口：
 *  - 先初始化 DataStore 并把它装进 SettingsRepository（等价 :app HanimeApplication 的
 *    DataStoreManager.initialize + SettingsRepository.install 两步；漏装会 UninitializedPropertyAccessException）。
 *    拦截器链里的 HanimeProxySelector/HanimeDns 等都会在第一次网络请求时读它，顺序必须在此之前；
 *  - Coil 桌面需注册单例 ImageLoader 并挂上 ktor3 网络取图器（否则图片不会加载）；
 *  - M2：使用须知/来源确认门控已进共享 App（与 Android 同语义），此处不再自动置位；
 *  - 受限网络下可用环境变量 HAN1ME_P3A_PROXY=host:port 给 JVM 设代理（否则走系统代理/直连）。
 */
fun main() {
    // 桌面"系统代理"模式（ProxyType.System，是设置里的默认值）：**JVM 默认不读 Windows 系统代理**，
    // sun.net.spi.DefaultProxySelector 只看 -Dhttp.proxyHost 这类系统属性 → 实际落到 DIRECT。
    // 后果不是"慢"，而是**完全连不上站点**：请求超时 → 连 CF 挑战页都拿不到 →
    // 用户看到的是"根本不弹 Cloudflare 验证窗口"（2026-09-13 实测坐实：proxy_type=1）。
    // 必须在 DefaultProxySelector 类加载之前设置；build.gradle.kts 的 jvmArgs 有同样一条
    // （打包产物走那条），这里是兜底。
    System.setProperty("java.net.useSystemProxies", "true")

    // M5-3：尽早注册未捕获异常处理器（落盘报告 + 退出，下次启动展示崩溃页）
    installCrashHandler()
    // M6-2：下载引擎端到端冒烟（HAN1ME_SMOKE=download，跑完即退）
    if (runSmokeIfRequested()) return
    // M7-2：CF cookie 落盘冒烟（HAN1ME_SMOKE=cookie-write / cookie-read，跑完即退）
    if (runCookieSmokeIfRequested()) return
    // 主题预生成（HAN1ME_GEN_BOARDS=1，跑完即退，输出进 shared/.../ui/theme/）
    if (runGenBoardsIfRequested()) return
    application {
    System.getenv("HAN1ME_P3A_PROXY")?.takeIf { it.isNotBlank() }?.let { hp ->
        val idx = hp.lastIndexOf(':')
        if (idx > 0) {
            val host = hp.substring(0, idx)
            val port = hp.substring(idx + 1)
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", port)
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", port)
            LogUtil.d("Desktop", "main: JVM proxy=$host:$port (from HAN1ME_P3A_PROXY)")
        }
    }

    // 只初始化一次；任何网络/设置读取之前完成
    runBlocking {
        LogUtil.d("Desktop", "main: initializing DataStoreManager...")
        DataStoreManager.initialize()
        SettingsRepository.install(DataStoreManager)
        LogUtil.d("Desktop", "main: DataStore+SettingsRepository ready, baseUrl=${SettingsRepository.baseUrl}")
        // 把"实际会用的出口"打出来：System 模式曾因 JVM 不读系统代理而静默 DIRECT，
        // 表现成"根本不弹 CF 验证"，有这行就能一眼定位。
        // （proxyType：0=Direct 1=System 2=Http 3=Socks）
        val effectiveProxy = runCatching {
            HanimeProxySelector().select(URI(SettingsRepository.baseUrl))
        }.getOrNull()
        LogUtil.d("Desktop", "main: proxyType=${SettingsRepository.proxyType} -> $effectiveProxy")
        // M6-2：恢复未完成的下载队列（Room 里的 Downloading/Queued 任务）
        lovehan1me.core.platform.initializeDesktopDownloadQueue()
        LogUtil.d("Desktop", "main: download queue restored")
    }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    LogUtil.d("Desktop", "main: Coil singleton ImageLoader(ktor3) registered")
    Window(
        onCloseRequest = ::exitApplication,
        title = "LoveHan1me",
        // 桌面窗口按横屏比例（16:10）：480x800 是手机竖屏尺寸，桌面端用它会得到一个又高又窄的窗，
        // 宽屏的两栏/侧栏自适应布局根本进不去（那些断点都按宽度判定）。
        state = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        // M3：注入 AWT 窗口，供 Skia 渲染面定位 SkiaLayer（LocalWindow 垫片）。
        DesktopWindowHolder.window = window
        App(
            onExit = ::exitApplication,
            // M5-2：注入桌面窗口宿主，播放器全屏走 AWT setFullScreenWindow
            platformScreens = PlatformScreens(
                videoPageHost = remember { DesktopVideoPageHost() },
                // M5-5 / 阶段一⑩：CF 验证独立弹窗（CDP 可见窗口自动收割，
                // 本机无浏览器/失败/超时由窗口内转手动兜底；KCEF 已删除）。
                cloudflare = { route ->
                    CloudflareVerificationWindow(
                        url = route.url,
                        host = route.host,
                        onPassed = { onBack() },
                    )
                },
            ),
        )
    }
}
}
