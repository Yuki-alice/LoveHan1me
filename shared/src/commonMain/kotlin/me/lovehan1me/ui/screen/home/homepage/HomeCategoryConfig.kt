package me.lovehan1me.ui.screen.home.homepage

import me.lovehan1me.Res
import me.lovehan1me.they_watched
import me.lovehan1me.ranking_today
import me.lovehan1me.ranking_this_month
import me.lovehan1me.mmd
import me.lovehan1me.latest_upload
import me.lovehan1me.latest_release
import me.lovehan1me.latest_hanime
import me.lovehan1me.latest_av
import me.lovehan1me.hd_uncensored
import me.lovehan1me.chinese_subtitle
import me.lovehan1me.chinese_amateur
import me.lovehan1me.china_av
import me.lovehan1me.category_motion_anime
import me.lovehan1me.category_instant_noodle
import me.lovehan1me.category_cosplay
import me.lovehan1me.category_3d_animation
import me.lovehan1me.animation_2d
import me.lovehan1me.animation_2_5d
import me.lovehan1me.amateur_nomask
import me.lovehan1me.ai_generated
import me.lovehan1me.ai_decensored
import me.lovehan1me.logic.SettingsRepository
import org.jetbrains.compose.resources.StringResource

const val HOME_CATEGORY_LATEST_HANIME = "latest_hanime"
const val HOME_CATEGORY_LATEST_RELEASE = "latest_release"
const val HOME_CATEGORY_LATEST_UPLOAD = "latest_upload"
const val HOME_CATEGORY_WATCHING_NOW = "watching_now"
const val HOME_CATEGORY_SHORT_EPISODE = "short_episode"
const val HOME_CATEGORY_MOTION_ANIME = "motion_anime"
const val HOME_CATEGORY_3D_CG = "3d_cg"
const val HOME_CATEGORY_2_5D = "2_5d"
const val HOME_CATEGORY_2D_ANIME = "2d_anime"
const val HOME_CATEGORY_AI_GENERATED = "ai_generated"
const val HOME_CATEGORY_MMD = "mmd"
const val HOME_CATEGORY_COSPLAY = "cosplay"

data class HomeCategoryPreferenceItem(
    val key: String,
    val normalTitleRes: StringResource,
    val avTitleRes: StringResource? = null,
)

val defaultHomeCategoryPreferenceItems = listOf(
    HomeCategoryPreferenceItem(HOME_CATEGORY_LATEST_HANIME, Res.string.latest_hanime, Res.string.latest_av),
    HomeCategoryPreferenceItem(HOME_CATEGORY_LATEST_RELEASE, Res.string.latest_release),
    HomeCategoryPreferenceItem(HOME_CATEGORY_LATEST_UPLOAD, Res.string.latest_upload),
    HomeCategoryPreferenceItem(HOME_CATEGORY_WATCHING_NOW, Res.string.they_watched),
    HomeCategoryPreferenceItem(HOME_CATEGORY_SHORT_EPISODE, Res.string.category_instant_noodle, Res.string.amateur_nomask),
    HomeCategoryPreferenceItem(HOME_CATEGORY_MOTION_ANIME, Res.string.category_motion_anime, Res.string.hd_uncensored),
    HomeCategoryPreferenceItem(HOME_CATEGORY_3D_CG, Res.string.category_3d_animation, Res.string.ai_decensored),
    HomeCategoryPreferenceItem(HOME_CATEGORY_2_5D, Res.string.animation_2_5d, Res.string.china_av),
    HomeCategoryPreferenceItem(HOME_CATEGORY_2D_ANIME, Res.string.animation_2d, Res.string.chinese_amateur),
    HomeCategoryPreferenceItem(HOME_CATEGORY_AI_GENERATED, Res.string.ai_generated, Res.string.chinese_subtitle),
    HomeCategoryPreferenceItem(HOME_CATEGORY_MMD, Res.string.mmd, Res.string.ranking_today),
    HomeCategoryPreferenceItem(HOME_CATEGORY_COSPLAY, Res.string.category_cosplay, Res.string.ranking_this_month),
)

val defaultHomeCategoryOrder: List<String>
    get() = defaultHomeCategoryPreferenceItems.map { it.key }

val homeCategoryOrder: List<String>
    get() = normalizeHomeCategoryKeys(SettingsRepository.current.homeCategoryOrder)

val hiddenHomeCategoryKeys: Set<String>
    get() = SettingsRepository.current.hiddenHomeCategoryKeys

suspend fun saveHomeCategoryPreferences(order: List<String>, hiddenKeys: Set<String>) =
    SettingsRepository.setHomeCategories(
        normalizeHomeCategoryKeys(order),
        hiddenKeys.filterTo(linkedSetOf()) { it in defaultHomeCategoryOrder },
    )

private fun normalizeHomeCategoryKeys(keys: List<String>): List<String> {
    val defaults = defaultHomeCategoryOrder
    return keys.distinct().filter { it in defaults } + defaults.filterNot { it in keys }
}
