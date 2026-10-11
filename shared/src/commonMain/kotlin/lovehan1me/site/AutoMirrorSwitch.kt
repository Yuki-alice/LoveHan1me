package lovehan1me.site

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.AutoMirrorGuard
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.hostOfUrl

/**
 * 自动镜像切换编排器（B3-3b / B3-4，档 A「失效才切」）。
 *
 * ## 三处触发（都汇到这里，判定只有一份）
 * - **启动**：`HomePageViewModel.initializeHomePage`（后台并发，不阻塞首屏）；
 * - **换网**：`NetworkChangeReactions` 的异步钩子（`setNetworkChangeHandler`）；
 * - **请求失败**：`NetworkRepo.ioRequest` 传输层失败后调用（`notifyUi = false`，由那条链自己重试）。
 *
 * ## 语义（本项最容易做错的地方）
 * **只在"当前域探测失败"时才切**；当前域可达时**绝不因为别的镜像更快而换站**。
 * 这同时守住两条相互拉扯的约束：
 * - P3"主镜像不可用时要能自动换" —— 当前域不可达时不切就卡死；
 * - "尊重用户显式选择" —— 用户明明选了 hanime1.me，没有坏掉就不该被换走。
 *
 * ## 只回答"该不该切、切到哪"，副作用最小
 * 判定（探测 + 选优 + guard）与切换动作分开：判定可离线注入 [probeOne] 断言，
 * 切换默认落到 [SiteSwitcher.autoSwitchTo]（轻量、不递增 generation）。
 *
 * ## 候选范围的两条硬边界
 * 1. **不跨站点类别**：番剧站（hanime1.me / .com / hanimeone.me）之间才互切，
 *    绝不把用户从番剧页切到 AV 站（javchu）——那是换内容语义，不是换镜像；
 * 2. **用户开了自定义镜像就不自动切**：自定义入口是用户的显式选择，自动切换不与它对撞。
 */
object AutoMirrorSwitch {

    /** 一次评估的结果（供调用方/测试区分"没切"与"想切但被 guard 拦下"）。 */
    sealed interface Result {
        /** 当前域可达 —— 不动站点（档 A 的常态）。 */
        data object Kept : Result

        /** 没有同类候选（如当前在 AV 站，或开了自定义镜像）。 */
        data object NoCandidate : Result

        /** 选出了目标但被 [AutoMirrorGuard] 拦下（目标域无 clearance 存档）。 */
        data class Skipped(val targetHost: String) : Result

        /** 已切换。 */
        data class Switched(val fromHost: String?, val toHost: String) : Result
    }

    /** 自动切换发生后的信号：UI 侧据它刷新当前页（切换本身不重建 composition）。 */
    private val _switched = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val switched = _switched.asSharedFlow()

    /** 串行化，避免启动与换网同时在途时重复评估/重复切换。 */
    private val mutex = Mutex()

    /**
     * 评估并（必要时）执行一次自动切换。
     *
     * @param probeOne 单镜像探测（RTT 毫秒；不可达 null）。默认走真实网络 [MirrorProbeClient]。
     * @param switchTo 切换动作。默认 [SiteSwitcher.autoSwitchTo]。
     * @param nowMs 供 [MirrorProbe.bestOf] 判定熔断用。
     * @param notifyUi 切换成功后是否发 [switched] 信号让 UI 刷新当前页。
     *   **失败触发路径传 false** —— 那条链自己会重试原请求并拿到新域内容，再发一次刷新信号
     *   会与它的重试互相打断（`getHomePage` 会 cancel 在途的同类任务）。
     */
    suspend fun run(
        probeOne: suspend (String) -> Long? = MirrorProbeClient::probe,
        switchTo: suspend (String) -> Unit = SiteSwitcher::autoSwitchTo,
        nowMs: Long = currentEpochMillis(),
        notifyUi: Boolean = true,
    ): Result = mutex.withLock {
        // 用户开了自定义镜像：尊重其显式入口，不自动切。
        if (SettingsRepository.useCustomMirrorSite) return@withLock Result.NoCandidate

        val currentDomain = SettingsRepository.domainName
        val currentHost = hostOfUrl(currentDomain)
        val candidates = sameClassMirrors(currentDomain)
        if (currentHost == null || candidates.isEmpty()) return@withLock Result.NoCandidate

        // ① 先探当前域：可达即收工（档 A 的核心 —— 不因"别人更快"而换）。
        if (probeOne(currentHost) != null) return@withLock Result.Kept

        // ② 当前域不可达 → 并发探同类候选，选最优。
        val hosts = candidates.map { it.first }
        val probeResult = MirrorProbe.probe(hosts, probeOne)
        // 没有任何候选探通：不切。`MirrorProbe.bestOf` 会在"无探测数据"时回退档案排序给出
        // 一个答案（那是给"推荐"用的）；但自动切换**盲目切到一个没探通的域**只会更糟
        // （可能弹 CF、可能同样打不开）。档 A 的保守在这里体现为：宁可留在原域报错。
        if (probeResult.isEmpty()) return@withLock Result.NoCandidate

        val best = MirrorProbe.bestOf(hosts, probeResult, nowMs) ?: return@withLock Result.NoCandidate
        val targetUrl = candidates.firstOrNull { it.first == best }?.second ?: return@withLock Result.NoCandidate

        // ③ guard 前置检查：默认只在目标域已有 clearance 存档时才切（否则必弹验证窗）。
        if (SettingsRepository.autoMirrorGuard == AutoMirrorGuard.RequireClearance &&
            SettingsRepository.cfCookieFor(best) == null
        ) {
            LogUtil.i(TAG, "自动切换被 guard 拦下：$best 无 clearance 存档（当前域 $currentHost 不可达）")
            return@withLock Result.Skipped(best)
        }

        switchTo(targetUrl)
        LogUtil.i(TAG, "已自动切换镜像：$currentHost → $best")
        if (notifyUi) _switched.tryEmit(Unit)
        Result.Switched(currentHost, best)
    }

    /**
     * 与 [currentDomain] **同类**的候选镜像（host to 目标域名 URL），排除当前域。
     *
     * 当前在 AV 站（javchu，唯一 AV 站）→ 无同类候选；
     * 当前在番剧站 → [HanimeConstants.ANIME_URL] 去掉当前项。未识别的域名一律按"无候选"处理。
     */
    private fun sameClassMirrors(currentDomain: String): List<Pair<String, String>> {
        if (SiteIdentity.isAvSite(currentDomain)) return emptyList()
        if (!SiteIdentity.isAnimeSite(currentDomain)) return emptyList()

        val currentHost = hostOfUrl(currentDomain)
        return HanimeConstants.ANIME_URL.mapNotNull { url ->
            val host = hostOfUrl(url) ?: return@mapNotNull null
            if (host == currentHost) null else host to url
        }
    }

    private const val TAG = "AutoMirrorSwitch"
}
