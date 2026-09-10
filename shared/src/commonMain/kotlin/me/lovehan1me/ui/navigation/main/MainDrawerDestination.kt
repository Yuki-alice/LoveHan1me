package me.lovehan1me.ui.navigation.main

import me.lovehan1me.Res
import me.lovehan1me.check_in_feature_name
import me.lovehan1me.download
import me.lovehan1me.fav_video
import me.lovehan1me.home_page
import me.lovehan1me.ic_access_time
import me.lovehan1me.my_subscribe
import me.lovehan1me.play_list
import me.lovehan1me.settings
import me.lovehan1me.watch_history
import me.lovehan1me.watch_later
import org.jetbrains.compose.resources.StringResource
import me.lovehan1me.ic_download
import me.lovehan1me.ic_favorite_border
import me.lovehan1me.ic_format_list_bulleted
import me.lovehan1me.ic_history
import me.lovehan1me.ic_home
import me.lovehan1me.ic_settings
import me.lovehan1me.ic_subscribtion
import me.lovehan1me.ic_thumb_up_off_alt
import me.lovehan1me.ui.navigation.settings.HomeSettingsRoute
import org.jetbrains.compose.resources.DrawableResource

enum class MainDrawerDestination(
    val route: HanimeScreen,
    val iconRes: DrawableResource,
    val titleRes: StringResource,
) {
    Home(
        route = HomeRoute,
        iconRes = Res.drawable.ic_home,
        titleRes = Res.string.home_page,
    ),
    Settings(
        route = HomeSettingsRoute,
        iconRes = Res.drawable.ic_settings,
        titleRes = Res.string.settings,
    ),
    DailyCheckIn(
        route = DailyCheckInRoute,
        iconRes = Res.drawable.ic_thumb_up_off_alt,
        titleRes = Res.string.check_in_feature_name,
    ),
    WatchLater(
        route = MyWatchLaterRoute,
        iconRes = Res.drawable.ic_access_time,
        titleRes = Res.string.watch_later,
    ),
    FavVideo(
        route = MyFavVideoRoute,
        iconRes = Res.drawable.ic_favorite_border,
        titleRes = Res.string.fav_video,
    ),
    Playlist(
        route = MyPlaylistRoute,
        iconRes = Res.drawable.ic_format_list_bulleted,
        titleRes = Res.string.play_list,
    ),
    Subscription(
        route = SubscriptionRoute,
        iconRes = Res.drawable.ic_subscribtion,
        titleRes = Res.string.my_subscribe,
    ),
    WatchHistory(
        route = WatchHistoryRoute,
        iconRes = Res.drawable.ic_history,
        titleRes = Res.string.watch_history,
    ),
    Download(
        route = DownloadRoute,
        iconRes = Res.drawable.ic_download,
        titleRes = Res.string.download,
    );

    companion object {
        fun fromRoute(route: HanimeScreen?): MainDrawerDestination? =
            entries.firstOrNull { it.route == route }
    }
}
