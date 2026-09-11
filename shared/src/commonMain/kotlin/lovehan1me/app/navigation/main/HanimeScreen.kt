package lovehan1me.app.navigation.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

interface HanimeScreen : NavKey

@Serializable
object HomeRoute : HanimeScreen

/**
 * 一级目的地「发现」的**顶层栈键**。
 *
 * 内容仍是原来的搜索界面（[SearchRoute] + `SearchScreen`），只是：
 *  - 语义从「一个输入框」升级为「一个探索空间」（搜索历史 / 热门标签 / 高级筛选都在这栏）；
 *  - 图标换指南针、标签改「发现」。
 *
 * 为什么不复用 [SearchRoute] 作顶层键：它是带参 data class，而一级 tab 需要一个
 * 与查询无关的稳定键——否则「带 query 进发现页」会变成换 tab 而不是同 tab 内换内容。
 */
@Serializable
object DiscoverTab : HanimeScreen

/**
 * 一级目的地「我的」的顶层栈键。
 *
 * 内容：签到首卡 + 登录账户卡 + 6 个内容入口（收藏 / 稍后看 / 播放列表 / 订阅 /
 * 观看历史 / 下载）。原「资料库」升格并重新聚合。
 */
@Serializable
object MineTab : HanimeScreen

@Serializable
object WatchHistoryRoute : HanimeScreen

@Serializable
object MyFavVideoRoute : HanimeScreen

@Serializable
object MyWatchLaterRoute : HanimeScreen

@Serializable
object MyPlaylistRoute : HanimeScreen

@Serializable
object SubscriptionRoute : HanimeScreen

@Serializable
object DailyCheckInRoute : HanimeScreen

@Serializable
object DownloadRoute : HanimeScreen

@Serializable
object AccountRoute : HanimeScreen

@Serializable
object LoginRoute : HanimeScreen

@Serializable
object ManualCookiesRoute : HanimeScreen

@Serializable
data class CloudflareRoute(
    val url: String,
    val host: String,
) : HanimeScreen

@Serializable
data class AvatarCropRoute(
    val sourceUri: String,
) : HanimeScreen

@Serializable
data class SearchRoute(
    val query: String? = null,
    val advancedSearchJson: String? = null,
) : HanimeScreen

@Serializable
object PreviewRoute : HanimeScreen

@Serializable
object GetchuPreviewRoute : HanimeScreen

@Serializable
data class GetchuPreviewDetailRoute(
    val id: String,
) : HanimeScreen

@Serializable
data class PreviewCommentRoute(
    val date: String,
    val dateCode: String,
) : HanimeScreen

@Serializable
data class VideoRoute(
    val videoCode: String,
    val localUri: String? = null,
) : HanimeScreen
