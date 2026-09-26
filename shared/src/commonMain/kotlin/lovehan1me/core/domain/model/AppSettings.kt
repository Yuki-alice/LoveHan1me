package lovehan1me.core.domain.model

import lovehan1me.video.contract.VideoEnhancementLevels

val DOWNLOAD_SPEED_BYTES = longArrayOf(
    0L,
    128 * 1024L,
    256 * 1024L,
    512 * 1024L,
    1024 * 1024L,
    2048 * 1024L,
    4096 * 1024L,
    8192 * 1024L,
    10240 * 1024L,
)

/**
 * 长按速播倍率读侧钳制到 2.0~5.0（设置页选项表 `LONG_PRESS_SPEED_CHOICES` 同界）。
 * 不做数据迁移——历史值（1.x、>5 的备份导入）读出即落到边界。
 * 不做 0.5 网格吸附：靠设置页选项收敛，钳制只兜越界脏值。
 */
fun normalizeLongPressSpeed(storedValue: Float): Float = storedValue.coerceIn(2f, 5f)

enum class ThemeMode(val value: String) {
    Light("always_off"),
    Dark("always_on"),
    System("follow_system");

    companion object {
        fun fromValue(value: String): ThemeMode = entries.firstOrNull { it.value == value } ?: Light
    }
}

enum class PaletteStyle(val id: Int) {
    TonalSpot(1), Neutral(2), Vibrant(3), Expressive(4), Rainbow(5), FruitSalad(6),
    Fidelity(7), Content(8);

    companion object {
        fun fromId(id: Int): PaletteStyle = entries.firstOrNull { it.id == id } ?: TonalSpot
    }
}

// PlayerKernel / VideoAspectMode / PictureAdjust 已搬入 :video:contract（本包 PlaybackModels.kt 只留别名）
//（包名不变，全仓 import 零改动；本文件持久化它们，见下）。

enum class ProxyType(val id: Int) {
    Direct(0), System(1), Http(2), Socks(3);

    companion object {
        fun fromId(id: Int): ProxyType = entries.firstOrNull { it.id == id } ?: System
    }
}

enum class DisplayDensity(val percent: Int, val scale: Float) {
    Compact(75, 0.75f),
    Default(100, 1f),
    Comfortable(125, 1.25f);

    companion object {
        fun fromPercent(percent: Int): DisplayDensity =
            entries.firstOrNull { it.percent == percent } ?: Default
    }
}

/**
 * 底栏形态（P6）。**只影响 Compact 宽度下的底栏**，Medium+ 的 NavigationRail 不受影响。
 *
 * - [Standard]：M3 `NavigationBar` 贴底；
 * - [Floating]：三个 pill 装在圆角胶囊容器内，浮在内容之上（距底 16dp + 手势 inset）。
 */
enum class NavBarStyle(val value: String) {
    Standard("standard"),
    Floating("floating");

    companion object {
        fun fromValue(value: String): NavBarStyle =
            entries.firstOrNull { it.value == value } ?: Standard
    }
}

/**
 * 动态对比度档位。
 *
 * [spec] 直接喂给 m3color 的 scheme 构造（HCT 对比度偏移，合法范围 -1.0 ~ 1.0）。
 * 三档取值照 M3 规范：standard = 0.0、medium = 0.5、high = 1.0。
 * 没有提供「降低对比度」（负值）—— 那会让可读性下降，不属于设置项该鼓励的方向。
 */
enum class ContrastLevel(val value: String, val spec: Double) {
    Standard("standard", 0.0),
    Medium("medium", 0.5),
    High("high", 1.0);

    companion object {
        fun fromValue(value: String): ContrastLevel =
            entries.firstOrNull { it.value == value } ?: Standard
    }
}

data class AppSettings(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.Light,
    /**
     * 命名主题槽位 id（见 `ThemeBoard`：sakura/take/sou/yuzu/midnight/nord/mono/system）。
     * 旧的 `useDynamicColor/themeAccent/paletteStyle` 三件套已删除，无用户、无迁移。
     */
    val themeId: String = "sakura",
    /** AMOLED 纯黑：与深浅正交的独立开关，只在深色下叠加（Mihon 模式）。 */
    val amoled: Boolean = false,
    /**
     * 动态对比度（审计 P2 接通）。
     *
     * 此前 `Theme.kt` 把它写死成 0.0 —— M3 Expressive 的「动态对比度」支柱等于没接，
     * 8 种调色板的自由度被砍掉一半。**默认档即原行为（0.0）**，所以老用户升级后
     * 视觉零变化，只有主动去设置里调才会变。
     */
    val contrastLevel: ContrastLevel = ContrastLevel.Standard,
    val allowPipMode: Boolean = true,
    val secureMode: Boolean = false,
    val disableComments: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val navBarStyle: NavBarStyle = NavBarStyle.Standard,
    val usageNoticeAccepted: Boolean = false,
    val isAlreadyLogin: Boolean = false,
    val localListNoticeDismissed: Boolean = false,
    val savedUserId: String = "",
    val loginCookie: String = "",
    /**
     * Cloudflare `cf_clearance` 按域存档：host（小写）→ Cookie 头。
     *
     * 为什么不是"一条 cookie + 一个 host"：Hanime1 有四个可切域名外加用户自定义镜像，
     * 而 clearance 是**按 zone 签发**的。单行存储下"在 B 域验证成功"会把 A 域那条还能用的
     * 凭据直接顶掉，用户看到的就是"切个镜像又弹一次验证窗、验完回来又弹"。
     */
    val cfCookies: Map<String, String> = emptyMap(),
    val domainName: String = "https://hanime1.me/",
    val selectedBaseUrl: String = "https://hanime1.me/",
    val useCustomMirrorSite: Boolean = false,
    val customMirrorSite: String = "",
    val appendCustomMirrorPath: Boolean = true,
    val useBuiltInHosts: Boolean = false,
    /**
     * 自动档（默认开）：Hanime 系域名**优先**走内置/自定义 IP，但先做一次连通性
     * 探测，全不通就退回 DoH / 系统 DNS —— 不像 [useBuiltInHosts] 那样强制、无回退。
     *
     * 为什么必须默认开（2026-09-21 本机实测）：系统 DNS 对 `hanime1.me` /
     * `www.hanime1.me` / `hanimeone.me` 全部返回**不可达**的假 IP（443 握手超时，
     * `hanimeone.me` 甚至落到 Facebook 段），而内置 CF IP 实测 5 个里 3 个通
     * （170–230ms）。不开这个，默认用户根本连不上站点。
     *
     * 为什么不能直接把 [useBuiltInHosts] 强制档打开：那批 IP 会失效，强制档一旦
     * 失效就是断网，而它又没有回退。"探测 + 回退"才是可以默认打开的形态。
     */
    val autoBuiltInHosts: Boolean = true,
    /**
     * 是否启用本地 ECH 网关（`echgate`）出站。
     *
     * 默认开（实验性）：项目的目标是免梯直连，网关失败时所有改写层自动放行、
     * 走原有机制（代理 / 内置 hosts / DoH），与关闭行为一致，故默认开是安全的。
     * 无运行时的平台（移动端待接入）开着也无害。
     * 用户手动关掉后予以尊重，不再自愈拉起。
     */
    val useEchGate: Boolean = true,
    val customHostsData: String = "",
    val useDoH: Boolean = false,
    val dohPreset: String = "alidns",
    val dohCustomUrl: String = "",
    val dohBootstrapIps: String = "",
    val dohTimeoutSeconds: Int = 10,
    val proxyType: ProxyType = ProxyType.System,
    val proxyIp: String = "",
    val proxyPort: Int = -1,
    /**
     * 桌面专用：CF 验证浏览器**自报的真实 UA**（空 = 尚未采集）。
     *
     * 为什么不直接用常量：cf_clearance 绑定 UA，而浏览器 UA 又**不能伪造**
     * （实测把真实 Chrome 153 伪装成 149 会永远卡在挑战页）。
     * 于是改成采集真实值存这里，由 currentHttpUserAgent() 发给 HTTP 层。
     */
    val desktopBrowserUserAgent: String = "",
    val cachedUpdateJson: String? = null,
    val ignoredVersionCode: Int = -1,
    val downloadCountLimit: Int = 2,
    val downloadSpeedLimitIndex: Int = 0,
    val usePrivateStorage: Boolean = true,
    val safDownloadPath: String? = null,
    val collapseDownloadedGroup: Boolean = false,
    val playerKernel: PlayerKernel = PlayerKernel.ExoPlayer,
    val playerSpeed: Float = 1f,
    /** 长按速播倍率（对齐 animeko 默认 3x；设置里可改）。 */
    val longPressSpeedTime: Float = 3f,
    val videoLanguage: String = "zhs",
    val videoQuality: String = "1080P",
    /**
     * G2-3b：画面比例偏好（三端持久化）。
     *
     * 引擎不支持某一档时会静默降级，并把真实生效值报进引擎状态的 `videoAspect`，
     * 所以这里存的始终是"用户选的那个"，不是"引擎用成的那个" ——
     * 换到支持的引擎上（比如从 Exo 切到 mpv）用户的选择还能回来。
     */
    val videoAspect: VideoAspectMode = VideoAspectMode.Fit,
    /**
     * 超分（Anime4K）档位：0=关 / 1=效率 / 2=质量（见 [VideoEnhancementLevels]）。
     *
     * 与 [videoAspect] 同一套取舍：存的是**用户选的那个**，不是引擎用成的那个 ——
     * 档位不可用时引擎会降级并把生效值报回来，显示取生效值、落盘取请求值，
     * 换到支持的引擎上用户的选择还能回来。
     *
     * 新增键：老存档没有 `super_resolution`，读出即本默认值 OFF，与升级前
     * "每次进页面都是关"的语义一致；越界或换引擎导致的无效档位由
     * `resolveEnhancementLevel` 在调用侧降到 OFF，不把无效索引交给引擎。
     */
    val superResolutionLevel: Int = VideoEnhancementLevels.OFF,
    /** G2-3b：画面亮度（-100~100，0 = 原始）。仅 mpv 内核生效。 */
    val pictureBrightness: Float = 0f,
    /** G2-3b：画面对比度（-100~100，0 = 原始）。仅 mpv 内核生效。 */
    val pictureContrast: Float = 0f,
    /** G2-3b：画面饱和度（-100~100，0 = 原始）。仅 mpv 内核生效。 */
    val pictureSaturation: Float = 0f,
    val showPlayedIndicator: Boolean = true,
    val allowResumePlayback: Boolean = true,

    /**
     * 进入视频详情页是否**自动开始播放**。
     *
     * 默认 **false**（用户要求）：进去先停在封面 + 播放按钮，由用户决定何时开播 ——
     * 移动网络下尤其重要（此前是一进去就播，配上"移动数据提醒"才勉强兜住）。
     */
    val autoPlayOnEnter: Boolean = false,

    /**
     * 系列视频是否**播完自动连播下一集**。
     *
     * 默认 **true**：只有系列清单里"当前项的后一项"存在时才触发（见
     * `shouldAutoPlayNext`），单片不受影响；关掉后底栏仍保留手动「下一集」键。
     */
    val autoPlayNext: Boolean = true,

    /**
     * 弹幕总开关。默认 **true**：接入方式由构建内置（见 [DanmakuBuildCredentials]），
     * 不再需要用户先配凭据，所以没有理由默认关。
     *
     * 未内置凭据的构建（`local.properties` 里没这两行）会因 `DanmakuProvider.from`
     * 返回 null 而整条链路不构造，届时状态条显示「未配置·去设置」，不会白屏。
     */
    val danmakuEnabled: Boolean = true,
    /**
     * 站内评论投影为主源（默认开）：随视频页本来就要拉评论，边际成本为 0，
     * 且不消耗弹弹play 共享额度。关掉后回到纯弹弹链路。
     */
    val danmakuCommentEnabled: Boolean = true,
    /**
     * 弹弹play API 代理地址（空 = 直连官方 + 内置凭据签名）。
     *
     * 这里只作为**用户自己运维的代理**的填入口：填了就走它、并且**不发**签名头
     * （由代理自己代签）。按 URL 前缀拼接，不是 HTTP 代理 —— iOS 侧是 Darwin
     * 引擎，套不进 OkHttp 的代理链。
     */
    val danmakuProxyBase: String = "",
    /** 构建期注入，源码里恒为空；用户在设置页填的值优先。 */
    val danmakuAppId: String = DanmakuBuildCredentials.APP_ID,
    /**
     * 同上。设备上的这一份只存本地：
     * [lovehan1me.data.datastore.DataStoreManager] 的 AUTH_KEYS 已把它排除在备份导出之外。
     */
    val danmakuAppSecret: String = DanmakuBuildCredentials.APP_SECRET,
    /**
     * 弹幕字号（sp）。默认 18：15sp 在横屏 720p 上要凑近才读得清，
     * 而行高／车道数都由它推出来（见 `DanmakuLayer`），调它就等于同时调整了密度。
     */
    val danmakuFontSizeSp: Int = 18,
    /** 弹幕不透明度（百分比）。80 是"压得住亮场景、又不遮剧情"的那一档；100 会糊成人墙。 */
    val danmakuOpacityPercent: Int = 80,
    /**
     * 弹幕可用高度占画面的比例（百分比）。50 = 上半屏，与主流弹幕观看习惯一致。
     *
     * 下限不给到 25：手机横屏的可用高度本就一两百像素，两三条轨道会让引擎
     * 频繁"满载丢弃"（找不到车道是直接丢，不排队），用户看到的就是弹幕莫名稀少。
     */
    val danmakuDisplayAreaPercent: Int = 50,
    /** 弹幕速度（百分比，100 = 标准 = 一条弹幕 10 秒走完一个视口宽度）。 */
    val danmakuSpeedPercent: Int = 100,
    /**
     * 按类型显示：滚动 / 顶部 / 底部。
     *
     * 默认底部关闭 —— 与 Kazumi、animeko 一致：底部常驻字幕最挡剧情，
     * 且本项目的评论投影全是滚动，关底部不影响主源。
     */
    val danmakuShowScroll: Boolean = true,
    val danmakuShowTop: Boolean = true,
    val danmakuShowBottom: Boolean = false,
    val mpvProfile: String = "fast",
    val enableGpuNextRenderer: Boolean = false,
    val mpvInterpolation: Boolean = false,
    val mpvDeband: Boolean = true,
    val mpvFramedrop: Boolean = true,
    val mpvHwdec: String = "Auto",
    val mpvCacheSecs: Int = 60,
    val mpvTlsVerify: Boolean = true,
    val mpvNetworkTimeout: Int = 10,
    val customMpvParams: String = "",
    val searchArtistIgnoreVideoType: Boolean = false,
    val disableMobileDataWarning: Boolean = false,
    val funLoadingHints: Boolean = true,
    val checkInEnabled: Boolean = true,
    val searchGridColumnsCompact: Int = 2,
    val searchGridColumnsMedium: Int = 3,
    val searchGridColumnsExpanded: Int = 4,
    val searchGridColumnsLarge: Int = 5,
    val searchGridColumnsExtraLarge: Int = 6,
    val subscriptionArtistRows: Int = 1,
    val homeCategoryOrder: List<String> = emptyList(),
    val hiddenHomeCategoryKeys: Set<String> = emptySet(),
    /**
     * 命名筛选预设（用户手工保存的常用筛选组合）。
     *
     * 存盘时序列化成一个 JSON 字符串放在 DataStore 的 `search_filter_presets` 键上；
     * 因为 `BackupManager` 导出的是整份 preferences，所以预设自动进入备份，无需额外接线。
     */
    val searchFilterPresets: List<SearchFilterPreset> = emptyList(),
    val alwaysShowUpdateCard: Boolean = false,
    val displayDensity: DisplayDensity = DisplayDensity.Default,
)

/**
 * [host] 能用哪条记录：返回命中的**域键**（精确域 → 父域），都没有返回 null。
 *
 * 要返回键而不是值，是因为作废时必须删对那一条：请求打在 `www.hanime1.me`、
 * 凭据记在 `hanime1.me` 时，按请求域精确删会一个键都删不掉，死钥匙继续留在表里。
 *
 * 父域回落只在同一棵域名树下成立（`www.hanime1.me` 用 `hanime1.me` 的记录）；
 * `hanime1.me` 与 `hanimeone.me` 是两个不同的 Cloudflare zone，串用等于拿错钥匙开锁，
 * 表现就是"浏览器明明验证过了，应用还是 403"。
 */
fun Map<String, String>.cfCookieKeyFor(host: String): String? {
    val labels = host.lowercase().split('.')
    for (start in labels.indices) {
        val candidate = labels.subList(start, labels.size).joinToString(".")
        // 不回落到最后一段（TLD）：一条键为 "me" 的记录会服务整棵 .me 树下的陌生站。
        // 单段 host（localhost 镜像）只剩精确匹配这一档，不受影响。
        if (start > 0 && !candidate.contains('.')) continue
        if (get(candidate)?.isNotBlank() == true) return candidate
    }
    return null
}

/** 取 [this] 里能给 [host] 用的 CF clearance（见 [cfCookieKeyFor]）。 */
fun Map<String, String>.cfCookieFor(host: String): String? =
    cfCookieKeyFor(host)?.let { this[it] }
