package lovehan1me.app.navigation.main

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import lovehan1me.app.bridge.NoopVideoPageHost
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.feature.home.homepage.HomePageViewModel

/**
 * M4：平台专属能力的注入槽位（绞杀者过渡期设施）。
 *
 * 背景：共享导航 [SharedTopNavigation] 的 35 个路由中，有 6 个依赖
 * Android 硬能力（WebView 登录 / CF 人机验证 / ActivityResult 头像 picker /
 * `cn.mucute` 裁剪库 / SAF 下载目录 / WorkManager 下载），此前只能做成
 * `NavPlaceholder`。要让 Android 切换到共享 `App()` 又不丢功能，这里把
 * 这些页面抽象成"由平台壳注入的 Composable"，共享层只负责装载与回退。
 *
 * 约定：
 * - 槽位为 `null` 时共享层回退到 [NavPlaceholder]（桌面/iOS 当前行为不变）；
 * - 槽位实现仍在 `:app`，共享层不引入任何 Android 依赖；
 * - 这是过渡设施，随 P7 各能力补齐后逐个删除对应槽位。
 */

/** 注入实现可用的共享上下文。 */
data class PlatformNavScope(
    val backStack: TopLevelBackStack<HanimeScreen>,
    val homeViewModel: HomePageViewModel,
    val onBack: () -> Unit,
)

/**
 * 六个平台页面的注入集合；全部可空，null 表示用占位页。
 */
data class PlatformScreens(
    /** `DownloadRoute`：下载列表（依赖 WorkManager，P7）。 */
    val download: (@Composable PlatformNavScope.() -> Unit)? = null,
    /**
     * `AccountRoute`：账号页（依赖 ActivityResult 头像 picker）。
     * 两个参数用于与 `AvatarCropRoute` 之间传递裁剪结果。
     */
    val account: (
        @Composable PlatformNavScope.(
            pendingAvatarCropResult: String?,
            onAvatarCropResultConsumed: () -> Unit,
        ) -> Unit
    )? = null,
    /** `LoginRoute`：登录页（依赖 WebView）。 */
    val login: (@Composable PlatformNavScope.() -> Unit)? = null,
    /** `CloudflareRoute`：CF 人机验证（依赖 WebView）。 */
    val cloudflare: (@Composable PlatformNavScope.(route: CloudflareRoute) -> Unit)? = null,
    /**
     * `AvatarCropRoute`：头像裁剪（依赖 `cn.mucute` cropper）。
     * [onConfirm] 回传裁剪产物的绝对路径（共享层不碰 `java.io.File`）。
     */
    val avatarCrop: (
        @Composable PlatformNavScope.(
            sourceUri: String,
            onConfirm: (String) -> Unit,
        ) -> Unit
    )? = null,
    /** `DownloadSettingsRoute`：下载设置（依赖 SAF，P7）。实现需自带 Scaffold。 */
    val downloadSettings: (@Composable PlatformNavScope.() -> Unit)? = null,

    /**
     * 视频页的窗口宿主（PiP / 常亮 / 全屏 / 亮度 / 系统栏）。
     *
     * M5-2：`VideoRouteScreen` 下沉后该参数一直沿用默认 [NoopVideoPageHost]，
     * 导致 :app 的 `AndroidVideoPageHost`（PiP/全屏/亮度/常亮）无人注入、成为死代码。
     * 收敛后由平台壳注入：Android 传 `rememberAndroidVideoPageHost`，
     * 桌面传 `DesktopVideoPageHost`（AWT 全屏），iOS 暂用 Noop。
     */
    val videoPageHost: VideoPageHost = NoopVideoPageHost,
)

/**
 * 抽屉的注入上下文。
 *
 * 共享 [SharedMainDrawer] 目前是最小版（目的地列表 + 登录入口），而 Android 版
 * 还含头像、用户名、当前站点、切换站点、打卡入口、平板常驻抽屉与安全区 padding。
 * 收敛期间由 `:app` 通过本宿主注入完整抽屉，共享层不持有这些 Android 语义。
 */
data class DrawerHost(
    val backStack: TopLevelBackStack<HanimeScreen>,
    val homeViewModel: HomePageViewModel,
    val isLoggedIn: Boolean,
    val drawerState: DrawerState,
    /** 返回 true 表示已处理（调用方负责关闭抽屉）。 */
    val onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
    val onOpenAccount: () -> Unit,
    val onRequireLogin: () -> Unit,
)
