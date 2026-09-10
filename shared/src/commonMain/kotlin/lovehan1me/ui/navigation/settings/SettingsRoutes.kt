package lovehan1me.ui.navigation.settings

import lovehan1me.Res
import lovehan1me.about
import lovehan1me.developer_options
import lovehan1me.download_settings
import lovehan1me.h_keyframe_manage
import lovehan1me.h_keyframe_settings
import lovehan1me.mpv_advanced_settings
import lovehan1me.network_settings
import lovehan1me.open_source_license
import lovehan1me.player_settings
import lovehan1me.settings
import lovehan1me.settings_appearance
import lovehan1me.settings_data_privacy
import lovehan1me.settings_interface_interaction
import lovehan1me.settings_network_download
import lovehan1me.settings_video_playback
import lovehan1me.shared_h_keyframe_manage
import lovehan1me.ui.navigation.main.HanimeScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
object HomeSettingsRoute : HanimeScreen

@Serializable
object VideoPlaybackSettingsRoute : HanimeScreen

@Serializable
object NetworkDownloadSettingsRoute : HanimeScreen

@Serializable
object AppearanceSettingsRoute : HanimeScreen

@Serializable
object InterfaceInteractionSettingsRoute : HanimeScreen

@Serializable
object DataPrivacySettingsRoute : HanimeScreen

@Serializable
object DeveloperOptionsSettingsRoute : HanimeScreen

@Serializable
object AboutSettingsRoute : HanimeScreen

@Serializable
object OpenSourceLicensesRoute : HanimeScreen

@Serializable
object PlayerSettingsRoute : HanimeScreen

@Serializable
object NetworkSettingsRoute : HanimeScreen

@Serializable
object DownloadSettingsRoute : HanimeScreen

@Serializable
object MpvPlayerSettingsRoute : HanimeScreen

@Serializable
object HKeyframesRoute : HanimeScreen

@Serializable
object SharedHKeyframesRoute : HanimeScreen

@Serializable
object HKeyframeSettingsRoute : HanimeScreen

enum class SettingsDestinationSpec(
    val titleRes: StringResource,
    val showToolbar: Boolean = true,
) {
    Home(
        titleRes = Res.string.settings,
    ),
    VideoPlayback(
        titleRes = Res.string.settings_video_playback,
    ),
    NetworkDownload(
        titleRes = Res.string.settings_network_download,
    ),
    Appearance(
        titleRes = Res.string.settings_appearance,
    ),
    InterfaceInteraction(
        titleRes = Res.string.settings_interface_interaction,
    ),
    DataPrivacy(
        titleRes = Res.string.settings_data_privacy,
    ),
    DeveloperOptions(
        titleRes = Res.string.developer_options,
    ),
    About(
        titleRes = Res.string.about,
    ),
    OpenSourceLicenses(
        titleRes = Res.string.open_source_license,
    ),
    Player(
        titleRes = Res.string.player_settings,
    ),
    Network(
        titleRes = Res.string.network_settings,
    ),
    Download(
        titleRes = Res.string.download_settings,
    ),
    Mpv(
        titleRes = Res.string.mpv_advanced_settings,
    ),
    HKeyframes(
        titleRes = Res.string.h_keyframe_manage,
    ),
    SharedHKeyframes(
        titleRes = Res.string.shared_h_keyframe_manage,
    ),
    HKeyframeSettings(
        titleRes = Res.string.h_keyframe_settings,
    );

    val route: HanimeScreen
        get() = when (this) {
            Home -> HomeSettingsRoute
            VideoPlayback -> VideoPlaybackSettingsRoute
            NetworkDownload -> NetworkDownloadSettingsRoute
            Appearance -> AppearanceSettingsRoute
            InterfaceInteraction -> InterfaceInteractionSettingsRoute
            DataPrivacy -> DataPrivacySettingsRoute
            DeveloperOptions -> DeveloperOptionsSettingsRoute
            About -> AboutSettingsRoute
            OpenSourceLicenses -> OpenSourceLicensesRoute
            Player -> PlayerSettingsRoute
            Network -> NetworkSettingsRoute
            Download -> DownloadSettingsRoute
            Mpv -> MpvPlayerSettingsRoute
            HKeyframes -> HKeyframesRoute
            SharedHKeyframes -> SharedHKeyframesRoute
            HKeyframeSettings -> HKeyframeSettingsRoute
        }
}
