package lovehan1me.core.domain.model

/**
 * P4：自 :app 下沉。原 @Parcelize/: Parcelable 已删除——已核实 :app 内 MySubscriptions/
 * SubscriptionItem/SubscriptionVideosItem 无任何 Bundle/putParcelable/Intent 用法（Navigation3 序列化路由，
 * Parcelable 是 vestigial）。
 */
data class MySubscriptions(
    val subscriptions: List<SubscriptionItem>,
    val subscriptionsVideos: List<SubscriptionVideosItem>,
    val maxPage: Int
)

data class SubscriptionItem(
    val artistName: String,
    val avatar: String,
    /**
     * G2-1b-3 收尾：站内作者 id（`/user/{id}` 里的数字）。
     *
     * 空串 = 解析没拿到（订阅卡片上没有作者链接）。此时点击**回退到搜索**，
     * 而不是进作者页 —— 作者页没有 id 拉不出东西，硬跳只会给一个空白页。
     */
    val artistId: String = "",
)

data class SubscriptionVideosItem(
    override val title: String,
    override val coverUrl: String,
    override val videoCode: String,
    override val duration: String? = null,
    override val views: String? = null,
    override val reviews: String? = null,
    override val currentArtist: String? = null,
    override val uploadTime: String?= null,
) : VideoItemType
