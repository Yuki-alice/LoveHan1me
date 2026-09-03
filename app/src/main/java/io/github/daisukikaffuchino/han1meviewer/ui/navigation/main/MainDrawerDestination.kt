package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HomeSettingsRoute

enum class MainDrawerDestination(
    val route: HanimeScreen,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
) {
    Home(
        route = HomeRoute,
        iconRes = R.drawable.ic_home,
        titleRes = R.string.home_page,
    ),
    Settings(
        route = HomeSettingsRoute,
        iconRes = R.drawable.ic_settings,
        titleRes = R.string.settings,
    ),
    DailyCheckIn(
        route = DailyCheckInRoute,
        iconRes = R.drawable.ic_thumb_up_off_alt,
        titleRes = R.string.check_in_feature_name,
    ),
    WatchLater(
        route = MyWatchLaterRoute,
        iconRes = R.drawable.ic_access_time,
        titleRes = R.string.watch_later,
    ),
    FavVideo(
        route = MyFavVideoRoute,
        iconRes = R.drawable.ic_favorite_border,
        titleRes = R.string.fav_video,
    ),
    Playlist(
        route = MyPlaylistRoute,
        iconRes = R.drawable.ic_format_list_bulleted,
        titleRes = R.string.play_list,
    ),
    Subscription(
        route = SubscriptionRoute,
        iconRes = R.drawable.ic_subscribtion,
        titleRes = R.string.my_subscribe,
    ),
    WatchHistory(
        route = WatchHistoryRoute,
        iconRes = R.drawable.ic_history,
        titleRes = R.string.watch_history,
    ),
    Download(
        route = DownloadRoute,
        iconRes = R.drawable.ic_download,
        titleRes = R.string.download,
    );

    companion object {
        fun fromRoute(route: HanimeScreen?): MainDrawerDestination? =
            entries.firstOrNull { it.route == route }
    }
}
