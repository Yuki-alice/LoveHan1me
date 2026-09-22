package lovehan1me.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.platform.rebuildSystemProxy
import lovehan1me.core.util.TagLocalizer
import lovehan1me.data.SettingsRepository
import lovehan1me.data.clearMemoryCookies
import lovehan1me.data.clearWebCookies
import lovehan1me.data.network.CsrfTokenProvider
import lovehan1me.data.network.HanimeNetwork
import lovehan1me.data.network.ensureEchGateway
import lovehan1me.feature.video.PreviewCommentPrefetcher

/**
 * 数据源**热切换**的唯一入口。
 *
 * ---
 *
 * ### 它等价于上游的「改配置 + restart(killProcess = true)」
 *
 * 上游（Han1meViewer）切站靠重启进程来清状态。已核对
 * `references/Han1meViewer-main/.../utils/ActivityManager.kt:13-21`：
 *
 * ```kotlin
 * fun restart(killProcess: Boolean = true) {
 *     ...startActivity(launchIntent)   // 只是重新拉起 Activity
 *     if (killProcess) exitProcess(0)  // 然后杀进程
 * }
 * ```
 *
 * **通篇没有一行碰 Room / DataStore / 磁盘缓存** —— 所以重启清掉的只有**进程内状态**
 * （重启后读写的是同一份文件）。本对象做的事就是：**在进程内把那些状态逐个重建掉**，
 * 效果等价于一次重启，但不退进程、不用等冷启动、也不会出现「起一下就被自己杀掉」的观感。
 *
 * ### 执行顺序不能换
 *
 * 1. **先落配置** —— service 的 `baseUrl` 是构造期快照（见 [HanimeNetwork.rebuildNetwork]），
 *    必须在 `update{}` **之后**重建，否则新 service 仍读到旧 URL；
 * 2. **再重建网络传输与 service** —— 代理（`rebuildSystemProxy`）+ 五个 service；
 * 3. **再清进程级站点凭据（登录态除外）** —— 内存 cookie、WebView cookie、CSRF token；
 * 4. **再清进程级缓存** —— 那些不以站点为键、跨站会串台的 object；
 * 5. **最后递增 [generation]** —— UI 侧据此重建 composition 与 ViewModelStore。
 *
 * ### 持久层为什么不动
 *
 * 与上游一致（依据同 `ActivityManager.kt:13-21`：重启不清任何持久状态，故"等价于重启"
 * 就意味着持久层也该原样保留）：Room 四个库（历史 / 下载 / 收藏 / 打卡）、DataStore、
 * OkHttp 磁盘缓存、下载文件**全部保留**。实测两站 videoCode 数值区间重叠但内容互不覆盖
 * （hanime1 的 code 在 javchu 上返回 302），因此不会"点开看到另一个视频"，
 * 最坏只是列表里混着另一站的条目。跨站隔离是独立议题，不在热切换范围内。
 *
 * @see SiteIdentity
 */
object SiteSwitcher {

    private val _generation = MutableStateFlow(0)

    /**
     * 数据源代次。每次切换完成 +1。
     *
     * UI 侧用它作 `key(...)` 重建整棵 composition、并换一套 `ViewModelStoreOwner`
     * （见 `App.kt`）—— 这一步就是"软重启"。
     */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /**
     * 网域之外的可选配置项。传 `null` 表示「保持当前值不动」
     * （「切换站点」按钮只换站、不改镜像配置）。
     */
    data class MirrorSettings(
        val useCustomMirrorSite: Boolean,
        val customMirrorSite: String,
        val appendCustomMirrorPath: Boolean,
    )

    /**
     * 切换到 [domain]（形如 `https://javchu.com/`）。
     *
     * @param domain 目标网域，来自网域下拉或「切换站点」按钮的解析结果。
     * @param mirror 自定义镜像配置；`null` 表示不改。
     * @param onBeforeRecompose 在递增 [generation] **之前**执行的收尾动作
     *   （UI 侧用来撤下对话框等）。切换完成后才触发重组。
     */
    suspend fun switchTo(
        domain: String,
        mirror: MirrorSettings? = null,
        onBeforeRecompose: suspend () -> Unit = {},
    ) {
        val previous = SettingsRepository.domainName

        // 1) 落配置。selectedBaseUrl 的语义对齐上游 `MainActivity.kt:212-225`
        //    （`confirmSiteSwitch`，真行号；本注释此前误写成 `:163-166`，那里其实是
        //    生物识别代码 —— 引用上游务必核对，别凭印象写行号）：
        //      if (currentSite in ANIME_URL) it.copy(selectedBaseUrl = currentSite, domainName = avSite)
        //      else it.copy(selectedBaseUrl = selectedBaseUrl, domainName = selectedBaseUrl)
        //    即**只在「从番剧站离开」时记住旧站**，作为回程目标；从 AV 站切回时不覆写，
        //    否则会把回程目标也写成 AV 站、原地打转。
        //
        //    与上游的**路径差异**（语义等价，实现更显式）：上游在同一个 `if/else` 里
        //    同时算 domainName 与 selectedBaseUrl，回程时 domainName 取自 selectedBaseUrl；
        //    本实现把「目标是谁」提到 `resolveToggleTarget()` 先算好、由 `domain` 传入，
        //    这里只负责「要不要记住旧站」。好处是兜底逻辑（回程目标为空/指向 AV 站时
        //    退回番剧主站）只在一处，调用方（网域下拉、切站按钮）共用同一条路径。
        //    判定用 domainName 而非 baseUrl —— 后者会被自定义镜像覆盖（见 SiteIdentity）。
        SettingsRepository.update {
            it.copy(
                domainName = domain,
                selectedBaseUrl = if (SiteIdentity.isAnimeSite(previous)) previous else it.selectedBaseUrl,
                useCustomMirrorSite = mirror?.useCustomMirrorSite ?: it.useCustomMirrorSite,
                customMirrorSite = mirror?.customMirrorSite ?: it.customMirrorSite,
                appendCustomMirrorPath = mirror?.appendCustomMirrorPath ?: it.appendCustomMirrorPath,
            )
        }

        // 2) 重建网络：代理选择器 + 全部 service（此处才真正把新 baseUrl 装进去）
        //    + 确保 ECH 网关在运行（开着开关但进程死了，借切换复活；
        //    探测缓存的失效在 jvmMain rebuildSystemProxy 内，见该函数注释）。
        rebuildSystemProxy()
        HanimeNetwork.rebuildNetwork()
        ensureEchGateway()

        // 3) 清进程级站点凭据，但**保留登录态**。
        //
        //    javchu 与 hanime 后端共用同一套账号（订阅 / 历史 / 收藏互通，实站验证），
        //    同一个会话 ID 两边都认；`loginCookie` 又是按 host 重写的全局值，
        //    DataStore 里的 `isAlreadyLogin / loginCookie / savedUserId` 必须原样保留。
        //    之前这里调 `logout()` 是登录丢失的根因 —— 它还跟"等价于重启"矛盾：
        //    上游杀进程根本不清 DataStore，登录本来就该留下来。
        //
        //    只清真正跟"这次进程状态"绑定的：内存 cookie（各端清法见 HanimeAccount，
        //    iOS 顺带把已持久化的 CF cookie 也洗掉 —— CF clearance 本来就按域签发，
        //    旧站的留着对新站无用）、WebView cookie、CSRF token（各站页面里重新拿）。
        //    会话若在新站恰好过期，走原来的登录过期路径重新登录，与正常过期一致。
        clearMemoryCookies()
        clearWebCookies()
        CsrfTokenProvider.csrfToken = null

        // 4) 清进程级、非按站点分键的缓存
        TagLocalizer.invalidate()
        PreviewCommentPrefetcher.reset()

        // 5) 收尾 + 通知 UI 软重启
        onBeforeRecompose()
        _generation.value++
    }

    /**
     * 「切换站点」按钮的目标站点：
     *
     * - 当前在番剧站 → 去 AV 站（[HanimeConstants.AV_URL]）；
     * - 当前在 AV 站 → 回 [SettingsRepository.selectedBaseUrl]，即上次离开番剧站时记下的那个。
     *
     * 兜底：回程目标为空、或本身就指向 AV 站（老数据 / 异常配置）时退回番剧主站 ——
     * 否则会"点了没反应"。
     */
    fun resolveToggleTarget(): String {
        if (!SiteIdentity.isAvSite) return HanimeConstants.AV_URL
        val remembered = SettingsRepository.selectedBaseUrl
        return if (remembered.isNotBlank() && !SiteIdentity.isAvSite(remembered)) remembered
        else HanimeConstants.HANIME_URL.first()
    }

    /** 按 [resolveToggleTarget] 一键切换（「我的」页账号卡按钮的语义）。 */
    suspend fun toggle(onBeforeRecompose: suspend () -> Unit = {}) {
        switchTo(resolveToggleTarget(), onBeforeRecompose = onBeforeRecompose)
    }
}
