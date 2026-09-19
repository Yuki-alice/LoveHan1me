package lovehan1me.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.platform.rebuildSystemProxy
import lovehan1me.core.util.TagLocalizer
import lovehan1me.data.SettingsRepository
import lovehan1me.data.logout
import lovehan1me.data.network.CsrfTokenProvider
import lovehan1me.data.network.HanimeNetwork
import lovehan1me.feature.video.PreviewCommentPrefetcher

/**
 * 数据源**热切换**的唯一入口。
 *
 * ---
 *
 * ### 它等价于上游的「改配置 + restart(killProcess = true)」
 *
 * 上游（Han1meViewer）切站靠重启进程来清状态（`ActivityManager.restart`）。重启清掉的
 * 只有**进程内状态** —— Room / DataStore / 磁盘缓存一个都不清（重启后还是同一份文件）。
 * 所以这里做的事就是：**在进程内把那些状态逐个重建掉**，效果等价于一次重启，但不退进程、
 * 不用等冷启动、也不会出现「起一下就被自己杀掉」的闪退观感。
 *
 * ### 执行顺序不能换
 *
 * 1. **先落配置** —— service 的 `baseUrl` 是构造期快照（见 [HanimeNetwork.rebuildNetwork]），
 *    必须在 `update{}` **之后**重建，否则新 service 仍读到旧 URL；
 * 2. **再重建网络传输与 service** —— 代理（`rebuildSystemProxy`）+ 五个 service；
 * 3. **再清站点性凭据** —— 登录态、CSRF token（`loginCookie` 是全局单值、按当前 host
 *    无差别注入，不切站的 A 站 cookie 会被发给 B 站）；
 * 4. **再清进程级缓存** —— 那些不以站点为键、跨站会串台的 object；
 * 5. **最后递增 [generation]** —— UI 侧据此重建 composition 与 ViewModelStore。
 *
 * ### 持久层为什么不动
 *
 * 与上游一致：Room 四个库（历史 / 下载 / 收藏 / 打卡）、DataStore、OkHttp 磁盘缓存、
 * 下载文件**全部保留**。实测两站 videoCode 数值区间重叠但内容互不覆盖
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

        // 1) 落配置。selectedBaseUrl 的语义对齐上游 MainActivity.kt:163-166：
        //    只在「从番剧站离开」时记住旧站，作为「切换站点」按钮的回程目标；
        //    从 AV 站切回时不覆写，否则会把回程目标也写成 AV 站、原地打转。
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
        rebuildSystemProxy()
        HanimeNetwork.rebuildNetwork()

        // 3) 清站点性凭据
        logout()
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
