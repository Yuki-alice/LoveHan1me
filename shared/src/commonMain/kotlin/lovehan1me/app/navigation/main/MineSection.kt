package lovehan1me.app.navigation.main

import lovehan1me.Res
import lovehan1me.download
import lovehan1me.fav_video
import lovehan1me.ic_access_time
import lovehan1me.ic_download
import lovehan1me.ic_favorite_border
import lovehan1me.ic_format_list_bulleted
import lovehan1me.ic_history
import lovehan1me.ic_subscribtion
import lovehan1me.my_subscribe
import lovehan1me.play_list
import lovehan1me.watch_history
import lovehan1me.watch_later
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * 「我的」页的 6 个内容入口 —— 原抽屉里 6 个二级项的收敛归宿。
 *
 * 取代旧 `MainDrawerDestination`（9 项）中除首页/设置/签到之外的全部：抽屉退役后，
 * 收藏 / 稍后看 / 播放列表 / 订阅 / 观看历史 / 下载 全部降为「我的」的 L2，
 * 由同一套行模板渲染（[lovehan1me.feature.mine.MineScreen]）。
 *
 * | 入口 | 路由（已存在，逐字复用） | 登录前置 |
 * |---|---|---|
 * | 收藏 | [MyFavVideoRoute] | 否（未登录走本地库） |
 * | 稍后看 | [MyWatchLaterRoute] | 否（未登录走本地库） |
 * | 播放列表 | [MyPlaylistRoute] | 否（未登录走本地库） |
 * | 订阅 | [SubscriptionRoute] | **是**（无本地降级，未登录进去是空页） |
 * | 观看历史 | [WatchHistoryRoute] | 否（本地 + 在线双源） |
 * | 下载 | [DownloadRoute] | 否（本地 Room，与登录无关） |
 *
 * ⚠️ [requiresLogin] 只影响**行的置灰与点击提示**，不做硬拦截式隐藏：未登录用户
 * 仍要看得见「订阅」这个入口存在（点它 → 提示登录并跳登录页）。这与旧
 * `navigateDrawerDestination` 的 `loginRequiredDrawerItems` 语义一致，只是
 * 把「拒绝 + 提示」从导航函数搬到了行的点击处理里，因为「我的」本身不再要求登录。
 *
 * @property route 目标路由；沿用旧的抽屉目标路由，避免新增路由与改动调用点
 * @property requiresLogin 未登录时该行置灰；点击改为「提示登录 + 跳登录页」
 */
enum class MineSection(
    val route: HanimeScreen,
    val iconRes: DrawableResource,
    val titleRes: StringResource,
    val requiresLogin: Boolean = false,
) {
    Favorites(
        route = MyFavVideoRoute,
        iconRes = Res.drawable.ic_favorite_border,
        titleRes = Res.string.fav_video,
    ),
    WatchLater(
        route = MyWatchLaterRoute,
        iconRes = Res.drawable.ic_access_time,
        titleRes = Res.string.watch_later,
    ),
    Playlists(
        route = MyPlaylistRoute,
        iconRes = Res.drawable.ic_format_list_bulleted,
        titleRes = Res.string.play_list,
    ),
    Subscriptions(
        route = SubscriptionRoute,
        iconRes = Res.drawable.ic_subscribtion,
        titleRes = Res.string.my_subscribe,
        requiresLogin = true,
    ),
    History(
        route = WatchHistoryRoute,
        iconRes = Res.drawable.ic_history,
        titleRes = Res.string.watch_history,
    ),
    Downloads(
        route = DownloadRoute,
        iconRes = Res.drawable.ic_download,
        titleRes = Res.string.download,
    ),
}
