package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.annotation.StringRes
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.ic_access_time
import io.github.daisukikaffuchino.han1meviewer.ic_download
import io.github.daisukikaffuchino.han1meviewer.ic_favorite_border
import io.github.daisukikaffuchino.han1meviewer.ic_format_list_bulleted
import io.github.daisukikaffuchino.han1meviewer.ic_history
import io.github.daisukikaffuchino.han1meviewer.ic_home
import io.github.daisukikaffuchino.han1meviewer.ic_settings
import io.github.daisukikaffuchino.han1meviewer.ic_subscribtion
import io.github.daisukikaffuchino.han1meviewer.ic_thumb_up_off_alt
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import org.jetbrains.compose.resources.DrawableResource

enum class MainDrawerDestination(
    val route: HanimeScreen,
    val iconRes: DrawableResource,
    @param:StringRes val titleRes: Int,
) {
    Home(
        route = HomeRoute,
        iconRes = Res.drawable.ic_home,
        titleRes = R.string.home_page,
    ),
    Settings(
        route = HomeSettingsRoute,
        iconRes = Res.drawable.ic_settings,
        titleRes = R.string.settings,
    ),
    DailyCheckIn(
        route = DailyCheckInRoute,
        iconRes = Res.drawable.ic_thumb_up_off_alt,
        titleRes = R.string.check_in_feature_name,
    ),
    WatchLater(
        route = MyWatchLaterRoute,
        iconRes = Res.drawable.ic_access_time,
        titleRes = R.string.watch_later,
    ),
    FavVideo(
        route = MyFavVideoRoute,
        iconRes = Res.drawable.ic_favorite_border,
        titleRes = R.string.fav_video,
    ),
    Playlist(
        route = MyPlaylistRoute,
        iconRes = Res.drawable.ic_format_list_bulleted,
        titleRes = R.string.play_list,
    ),
    Subscription(
        route = SubscriptionRoute,
        iconRes = Res.drawable.ic_subscribtion,
        titleRes = R.string.my_subscribe,
    ),
    WatchHistory(
        route = WatchHistoryRoute,
        iconRes = Res.drawable.ic_history,
        titleRes = R.string.watch_history,
    ),
    Download(
        route = DownloadRoute,
        iconRes = Res.drawable.ic_download,
        titleRes = R.string.download,
    );

    companion object {
        fun fromRoute(route: HanimeScreen?): MainDrawerDestination? =
            entries.firstOrNull { it.route == route }
    }
}
