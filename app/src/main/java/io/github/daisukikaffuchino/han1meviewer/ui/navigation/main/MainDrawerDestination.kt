package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.check_in_feature_name
import io.github.daisukikaffuchino.han1meviewer.download
import io.github.daisukikaffuchino.han1meviewer.fav_video
import io.github.daisukikaffuchino.han1meviewer.home_page
import io.github.daisukikaffuchino.han1meviewer.ic_access_time
import io.github.daisukikaffuchino.han1meviewer.my_subscribe
import io.github.daisukikaffuchino.han1meviewer.play_list
import io.github.daisukikaffuchino.han1meviewer.settings
import io.github.daisukikaffuchino.han1meviewer.watch_history
import io.github.daisukikaffuchino.han1meviewer.watch_later
import org.jetbrains.compose.resources.StringResource
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
