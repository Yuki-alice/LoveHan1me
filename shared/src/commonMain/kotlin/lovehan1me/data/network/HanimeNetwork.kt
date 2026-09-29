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
 * 五个站点 service 的持有者，全部是**稳定单例**。
 *
 * 站点地址由各 service 每次请求从 `HANIME_BASE_URL`（其本身是读设置的 getter）实时解析；
 * 传输层（Ktor 客户端及其下的 OkHttp / Darwin 引擎）也与站点、设置无关 —— 所以无论是
 * 切换站点还是改任何网络设置，都不需要重建实例。
 */
object HanimeNetwork {

    private val hanimeHttpClient = createHanimeHttpClient()
    private val getchuHttpClient = createGetchuHttpClient()

    val hanimeService = HanimeBaseService(hanimeHttpClient)
    val getchuService = GetchuService(getchuHttpClient)
    val commentService = HanimeCommentService(hanimeHttpClient)
    val myListService = HanimeMyListService(hanimeHttpClient)
    val subscriptionService = HanimeSubscriptionService(hanimeHttpClient)
}
