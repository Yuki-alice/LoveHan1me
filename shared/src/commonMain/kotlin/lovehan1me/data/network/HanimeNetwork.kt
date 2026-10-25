package lovehan1me.data.network

import lovehan1me.data.network.service.GetchuService
import lovehan1me.data.network.service.HanimeBaseService
import lovehan1me.data.network.service.HanimeCommentService
import lovehan1me.data.network.service.HanimeMyListService
import lovehan1me.data.network.service.HanimeSubscriptionService

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/08 008 22:35
 *
 * 五个站点 service 的持有者。
 *
 * 站点地址由各 service 每次请求从 `HANIME_BASE_URL`（其本身是读设置的 getter）实时解析；
 * 传输层（Ktor 客户端及其下的 OkHttp / Darwin 引擎）与站点无关 —— 切换站点不需要重建实例。
 *
 * 网络优化 1.2：客户端改 `var` 并新增 [rebuildTransports] —— iOS 的应用内代理走
 * NSURLSession 会话配置，**会话配置在客户端创建时被拷贝**，改代理后只有换新客户端才生效。
 * 仅 iOS 经 `platformRebuildSystemProxy()` 调用（改代理 / 备份导入）；JVM 侧没有调用点，
 * 稳定单例语义不变。
 */
object HanimeNetwork {

    private var hanimeHttpClient = createHanimeHttpClient()
    private var getchuHttpClient = createGetchuHttpClient()

    var hanimeService = HanimeBaseService(hanimeHttpClient)
        private set
    var getchuService = GetchuService(getchuHttpClient)
        private set
    var commentService = HanimeCommentService(hanimeHttpClient)
        private set
    var myListService = HanimeMyListService(hanimeHttpClient)
        private set
    var subscriptionService = HanimeSubscriptionService(hanimeHttpClient)
        private set

    /**
     * 重建两个客户端与全部五个 service（设置页改代理 / 备份导入后的生效路径）。
     *
     * 重建不是原子的：在途请求继续跑在旧客户端上直到结束，新请求即刻走新客户端 ——
     * 这正是想要的语义（不闪断）。写入都发生在设置页的主协程里，无并发写。
     * 旧客户端不 close：在途请求还要用它；少量连接对象随 GC 回收（重建频率 = 改代理频率，可忽略）。
     */
    internal fun rebuildTransports() {
        hanimeHttpClient = createHanimeHttpClient()
        getchuHttpClient = createGetchuHttpClient()
        hanimeService = HanimeBaseService(hanimeHttpClient)
        getchuService = GetchuService(getchuHttpClient)
        commentService = HanimeCommentService(hanimeHttpClient)
        myListService = HanimeMyListService(hanimeHttpClient)
        subscriptionService = HanimeSubscriptionService(hanimeHttpClient)
    }
}
