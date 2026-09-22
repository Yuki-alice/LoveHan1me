package lovehan1me.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import lovehan1me.app.App
import lovehan1me.app.crash.installCrashHandler
import lovehan1me.app.navigation.main.PlatformScreens
import lovehan1me.app.web.CloudflareVerificationWindow
import lovehan1me.core.platform.applyAppLanguage
import lovehan1me.core.platform.initializeDesktopDownloadQueue
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.StartupTrace
import lovehan1me.data.SettingsRepository
import lovehan1me.data.datastore.DataStoreManager
import lovehan1me.data.network.EchGateProcess
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.data.network.createHanimeHttpClient
import lovehan1me.feature.player.DesktopMpvPlaybackEngine
import lovehan1me.feature.player.DesktopVideoPageHost
import lovehan1me.feature.player.DesktopWindowHolder
import lovehan1me.ui.component.content.LoadingContent
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ProxySelector
import java.net.URI

/**
 * M1 桌面入口：
 *  - 先初始化 DataStore 并把它装进 SettingsRepository（等价 :app HanimeApplication 的
 *    DataStoreManager.initialize + SettingsRepository.install 两步；漏装会 UninitializedPropertyAccessException）。
 *    拦截器链里的 HanimeProxySelector/HanimeDns 等都会在第一次网络请求时读它，顺序必须在此之前；
 *  - Coil 桌面需注册单例 ImageLoader 并挂上 ktor3 网络取图器（否则图片不会加载）；
 *  - M2：使用须知/来源确认门控已进共享 App（与 Android 同语义），此处不再自动置位；
 *  - 受限网络下可用环境变量 HAN1ME_P3A_PROXY=host:port 给 JVM 设代理（否则走系统代理/直连）。
 *
 * M5-2（启动性能）三件事：
 *  1. **冷启动分段埋点**：[StartupTrace]，起点在 main 第一行，段落为
 *     `application → datastore → settings → download-queue → coil → first-frame`；
 *  2. **初始化不再 `runBlocking` 阻塞**：改成 `LaunchedEffect` 里的挂起流程 + 超时保护，
 *     失败/超时渲染 [StartupFailureContent]（此前失败就是黑窗或
 *     `UninitializedPropertyAccessException` 直接崩，用户看不到任何信息）；
 *  3. **语言在启动时生效**（[applyAppLanguage]），对齐 Android 的
 *     `AppLanguageManager.applyStoredLanguage`。
 */
fun main() {
    // M5-2：埋点起点放在最前 —— 连崩溃处理器注册、冒烟判定都算进"启动"里。
    StartupTrace.begin("desktop:main")

    // 桌面"系统代理"模式（ProxyType.System，是设置里的默认值）：**JVM 默认不读 Windows 系统代理**，
    // sun.net.spi.DefaultProxySelector 只看 -Dhttp.proxyHost 这类系统属性 → 实际落到 DIRECT。
    // 后果不是"慢"，而是**完全连不上站点**：请求超时 → 连 CF 挑战页都拿不到 →
    // 用户看到的是"根本不弹 Cloudflare 验证窗口"（2026-09-13 实测坐实：proxy_type=1）。
    // 必须在 DefaultProxySelector 类加载之前设置；build.gradle.kts 的 jvmArgs 里有同样一条
    // （打包产物走那条），这里是兜底。
    System.setProperty("java.net.useSystemProxies", "true")

    // M5-3：尽早注册未捕获异常处理器（落盘报告 + 退出，下次启动展示崩溃页）
    installCrashHandler()
    // mpv 原生库启动预载（视频详情页 EDT 冻结的根因修复，见
    // DesktopMpvPlaybackEngine.preloadAsync 文档）：趁用户还在启动页/首页浏览，
    // 后台线程把 ~49MB dylib 的解压 + dlopen 消化掉。越早越好，放在 main() 里。
    DesktopMpvPlaybackEngine.preloadAsync()
    // 下载引擎端到端冒烟（HAN1ME_SMOKE=download，跑完即退）
    if (runSmokeIfRequested()) return
    // 主题预生成（HAN1ME_GEN_BOARDS=1，跑完即退，输出进 shared/.../ui/theme/）
    if (runGenBoardsIfRequested()) return

    application {
        StartupTrace.mark("application")
        // HAN1ME_P3A_PROXY 已移入 initializeDesktop（见下）：它必须在
        // rebuildNetwork() 之后生效，否则设置页的代理类型会把它清掉。

        // M5-2：Coil 单例注册是 **@Composable**（coil-compose 的 API），只能在组合里调，
        // 因此它留在 application 作用域、而不是下面的挂起初始化函数里。
        // 语义与原来一致：在任何图片请求之前注册好。
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components {
                    // 图片同样在 CDN 上（实测 `vdownload.hembed.com/image/…`），和视频一样
                    // 被 SNI 阻断。Coil 默认会**自建一个 Ktor 客户端**，那个客户端不带
                    // ECH 网关拦截器，结果就是页面能开、图一张都出不来。
                    // 显式复用应用自己的客户端，让图片与页面走同一条出口。
                    add(KtorNetworkFetcherFactory(httpClient = { createHanimeHttpClient() }))
                }
                .build()
        }

        // ── M5-2：初始化状态机（替代原来的 runBlocking 阻塞 + 黑窗）──────
        var attempt by remember { mutableStateOf(0) }
        var startup by remember { mutableStateOf<DesktopStartup>(DesktopStartup.Pending) }
        LaunchedEffect(attempt) {
            // 单例在上面的组合里已注册（顺序：组合 → 本 effect），此处只记时间点
            StartupTrace.mark("coil-register")
            startup = DesktopStartup.Pending
            val startedAt = System.currentTimeMillis()
            val outcome = withTimeoutOrNull(DESKTOP_STARTUP_TIMEOUT_MS) {
                runCatching { initializeDesktop() }
            }
            startup = when {
                // 超时：把"卡住了"变成可诊断的一句话，而不是永远空窗
                outcome == null -> DesktopStartup.Failed(
                    "初始化超时（${DESKTOP_STARTUP_TIMEOUT_MS / 1000} 秒内 DataStore/设置未就绪）。",
                )

                outcome.isFailure -> DesktopStartup.Failed(
                    outcome.exceptionOrNull()?.let { it.message ?: it::class.simpleName }
                        ?: "未知错误",
                )

                else -> DesktopStartup.Ready
            }
            val elapsed = System.currentTimeMillis() - startedAt
            LogUtil.i("Desktop", "startup: $startup（第 ${attempt + 1} 次尝试，耗时 ${elapsed}ms）")
            if (startup is DesktopStartup.Failed) {
                LogUtil.e("Desktop", "startup failed: ${(startup as DesktopStartup.Failed).message}")
            }
        }

        // 阶段 D：恢复上次的窗口几何（尺寸 / 位置 / 最大化）。
        // 默认 1280x800 是横屏 16:10 —— 480x800 那种手机竖屏尺寸会让桌面端
        // 永远进不去宽屏两栏布局（那些断点都按宽度判定）。
        val savedGeometry = remember { WindowStateStore.load() }
        val windowState = rememberWindowState(
            size = savedGeometry.size,
            position = savedGeometry.position,
            placement = savedGeometry.placement,
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "LoveHan1me",
            state = windowState,
        ) {
            // 窗口真正销毁时落盘（用 onDispose 而非 onCloseRequest：后者将来可能被
            // "最小化到托盘"之类的逻辑提前拦截，导致几何永远存不下来）。
            RememberWindowGeometry(windowState)
            when (val state = startup) {
                DesktopStartup.Pending -> StartupWaitingContent()

                is DesktopStartup.Failed -> StartupFailureContent(
                    message = state.message,
                    onRetry = { attempt += 1 },
                    onExit = ::exitApplication,
                )

                DesktopStartup.Ready -> {
                    // M3：注入 AWT 窗口，供 Skia 渲染面定位 SkiaLayer（LocalWindow 垫片）。
                    DesktopWindowHolder.window = window
                    // M5-2：真正的首帧 —— withFrameNanos 会挂起到下一帧被合成，
                    // 比"首次组合"更接近"用户真的看到画面"。
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        StartupTrace.mark("first-frame")
                        StartupTrace.summary()
                    }
                    // HAN1ME_AUTO_VIDEO=<code>：首页就绪后自动跳视频详情页（性能探针）。
                    // 用于无人工点击采集 PlayerTrace 全链路（engine-create/fetch/parse/
                    // load/first-frame 分段）；code 应选一个未进 http_cache 的视频，
                    // 才能复现"构建后首次点开"。150s 后自动退出，跑法：
                    // HAN1ME_AUTO_VIDEO=408236 ./gradlew :desktopApp:run
                    val autoVideoCode = remember {
                        System.getenv("HAN1ME_AUTO_VIDEO")?.takeIf { it.isNotBlank() }
                    }
                    App(
                        onExit = ::exitApplication,
                        autoNavigateVideoCode = autoVideoCode,
                        autoNavigateExitAfterMs = 150_000L,
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
    }
}

/** 桌面初始化状态（M5-2）。 */
private sealed interface DesktopStartup {
    data object Pending : DesktopStartup
    data object Ready : DesktopStartup
    data class Failed(val message: String) : DesktopStartup
}

/**
 * 初始化超时上限。
 *
 * 10 秒是"慢机器也够、真卡住又不至于让用户干等"的折中：DataStore 读的是几 KB 的
 * 本地文件，正常在几百毫秒内；超过 10 秒基本可以断定是卡住（磁盘异常/权限/死锁），
 * 此时给出可重试的失败页比继续空窗有用。
 */
private const val DESKTOP_STARTUP_TIMEOUT_MS = 10_000L

/**
 * 桌面初始化（挂起版；**不再 `runBlocking`**，因此不阻塞 AWT/Compose 线程）。
 *
 * 顺序即依赖顺序：DataStore → SettingsRepository → 语言 → 代理诊断 → 下载队列 → Coil。
 */
private suspend fun initializeDesktop() {
    LogUtil.d("Desktop", "main: initializing DataStoreManager...")
    DataStoreManager.initialize()
    StartupTrace.mark("datastore")
    SettingsRepository.install(DataStoreManager)
    StartupTrace.mark("settings")
    LogUtil.d("Desktop", "main: DataStore+SettingsRepository ready, baseUrl=${SettingsRepository.baseUrl}")

    // M5-2：语言必须在**任何资源读取之前**生效 —— 桌面此前只有 LanguageHelper 那条链路读设置，
    // Compose Resources（Res.string.*）仍按系统语言解析，表现为"语言设置看起来没生效"。
    // 放在设置就绪之后、界面组合之前（与 Android 在 Application.onCreate 里做同一件事）。
    applyAppLanguage(SettingsRepository.current.appLanguage)
    StartupTrace.mark("language")

    // 全局默认选择器 + 系统属性：HttpURLConnection 支路（CDP 调试端口轮询、
    // DoH 之外的裸连接）与 OkHttp 走同一套代理判定；此前桌面从未 setDefault，
    // 这些支路恒走 DefaultProxySelector，与设置页的代理完全脱节。
    // 必须在设置就绪后、任何网络请求前执行（此处即该位置）。
    ProxySelector.setDefault(HanimeProxySelector())
    HanimeProxySelector.rebuildNetwork()

    // 受限网络逃生舱：HAN1ME_P3A_PROXY=host:port 显式覆盖 JVM 代理。
    // 必须在 rebuildNetwork() 之后——否则设置页的代理类型（Direct/System）
    // 会把这里写的标准属性清掉，顺序反了等于没设。
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

    // 把"实际会用的出口"打出来：System 模式曾因 JVM 不读系统代理而静默 DIRECT，
    // 表现成"根本不弹 CF 验证"，有这行就能一眼定位。
    // （proxyType：0=Direct 1=System 2=Http 3=Socks）
    val effectiveProxy = runCatching {
        HanimeProxySelector().select(URI(SettingsRepository.baseUrl))
    }.getOrNull()
    LogUtil.d("Desktop", "main: proxyType=${SettingsRepository.proxyType} -> $effectiveProxy")

    // ECH 网关（真免梯）：仅开关打开时拉起。它内部自己判平台与产物是否齐备，
    // 失败只记日志——网关是加速项，不该让启动失败。
    //
    // 位置刻意靠前：首页图片在界面首帧后立刻开始加载，网关越早就绪越不容易漏掉。
    // 就绪时间主要取决于 ECH 公钥配置——首次启动要等一次 DoH，之后走 --cache-dir 的磁盘缓存。
    if (SettingsRepository.useEchGate) {
        runCatching { EchGateProcess.start() }
            .onFailure { LogUtil.w("Desktop", "main: ECH 网关启动失败：${it.message}") }
    }
    StartupTrace.mark("ech-gate")

    // 恢复未完成的下载队列（Room 里的 Downloading/Queued 任务）
    initializeDesktopDownloadQueue()
    StartupTrace.mark("download-queue")
    LogUtil.d("Desktop", "main: download queue restored")
}

/**
 * 初始化进行中的内容：替代黑窗/白窗。
 *
 * ⚠️ 这里**不能用 `HanimeTheme`** —— 它要读 SettingsRepository，而设置恰恰是
 * "还没初始化完"的那一环；用它就是拿失败去渲染失败页。故用 M3 默认主题。
 */
@Composable
private fun StartupWaitingContent() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            LoadingContent(message = "正在启动…")
        }
    }
}

/**
 * 初始化失败/超时页（M5-2）。
 *
 * 此前失败路径是：`runBlocking` 里抛 `UninitializedPropertyAccessException` → 窗口还没建 → 
 * 用户面对黑窗或闪退，**拿不到任何可反馈的信息**。这里把话说清楚并给两条出路：
 * 重试（往往是磁盘/权限的瞬时问题）或退出。同样不用 `HanimeTheme`（见上）。
 */
@Composable
private fun StartupFailureContent(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("启动失败", style = MaterialTheme.typography.titleLarge)
            Text(
                "应用初始化没能完成。可以先重试；若反复失败，请把下面这行信息反馈给开发者。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("重试") }
                OutlinedButton(onClick = onExit) { Text("退出") }
            }
        }
    }
}
