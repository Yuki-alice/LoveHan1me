package me.lovehan1me.logic.network

import me.lovehan1me.logic.network.service.GetchuService
import me.lovehan1me.logic.network.service.HanimeBaseService
import me.lovehan1me.logic.network.service.HanimeCommentService
import me.lovehan1me.logic.network.service.HanimeMyListService
import me.lovehan1me.logic.network.service.HanimeSubscriptionService

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:35
 *
 * P3：自 :app 下沉 commonMain。原 `ServiceCreator.create<T>(baseUrl)`（Retrofit）改为
 * Ktor expect 工厂 createXxxHttpClient() + 服务类构造（baseUrl 默认取常量，构造时快照，
 * 与旧 Retrofit create 时语义一致）。rebuildNetwork 保留：先重建底层传输
 * （JVM=ServiceCreator.rebuildOkHttpClient，iOS=no-op），再重建各 service 实例。
 */
object HanimeNetwork {
    var hanimeService = _hanimeService
        private set
    var getchuService = _getchuService
        private set
    var commentService = _commentService
        private set
    var myListService = _myListService
        private set
    var subscriptionService = _subscriptionService
        private set

    private val _hanimeService
        get() = HanimeBaseService(createHanimeHttpClient())

    private val _getchuService
        get() = GetchuService(createGetchuHttpClient())

    private val _commentService
        get() = HanimeCommentService(createHanimeHttpClient())

    private val _myListService
        get() = HanimeMyListService(createHanimeHttpClient())

    private val _subscriptionService
        get() = HanimeSubscriptionService(createHanimeHttpClient())

    fun rebuildNetwork() {
        rebuildHttpClients()
        hanimeService = _hanimeService
        getchuService = _getchuService
        commentService = _commentService
        myListService = _myListService
    }
}
