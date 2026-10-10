package lovehan1me.feature.home.homepage

import lovehan1me.Res
import lovehan1me.ai_decensored
import lovehan1me.ai_generated
import lovehan1me.amateur_nomask
import lovehan1me.animation_2_5d
import lovehan1me.animation_2d
import lovehan1me.category_3d_animation
import lovehan1me.category_cosplay
import lovehan1me.category_instant_noodle
import lovehan1me.category_motion_anime
import lovehan1me.china_av
import lovehan1me.chinese_amateur
import lovehan1me.chinese_subtitle
import lovehan1me.hd_uncensored
import lovehan1me.latest_av
import lovehan1me.latest_hanime
import lovehan1me.latest_release
import lovehan1me.latest_upload
import lovehan1me.core.domain.model.HomePage
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.mmd
import lovehan1me.ranking_this_month
import lovehan1me.ranking_today
import lovehan1me.they_watched

/** Hero 轮播总上限（运营位计入），5 个：指示器可读、自动翻页一轮 25 秒内看完。 */
const val HERO_MAX_ITEM_COUNT = 5

/**
 * 构建首页 Hero 轮播数据（移植 misaka-Han1meViewer `buildHomeHeroItems`）。
 *
 * 官网运营位固定排首；其后从下方分类行取视频补足，使单运营位时仍可翻页。
 * 视频优先取「他們在看」，其余按传入顺序（调用方已按用户设置排好序并滤掉隐藏分组，
 * 故此处不再读设置，保证纯函数可测），跳过与运营位重复的条目。
 * 总数封顶 [HERO_MAX_ITEM_COUNT]（运营位计入：有运营位时视频取 4，无时取 5）。
 *
 * [subtitleFormatter] 默认走 `DisplayTextLocalizer`（读语言设置，生产用）；
 * 单测注入原文拼接，避免 `SettingsRepository` 未安装（见 `HomeHeroItemsTest`）。
 *
 * @param homePage 仓库层首页原始数据。
 * @param categories 已过滤排序的分类行。
 */
fun buildHomeHeroItems(
    homePage: HomePage,
    categories: List<HomeCategory>,
    subtitleFormatter: (artist: String?, views: String?, time: String?) -> String? = ::defaultHeroSubtitle,
): List<HomeHeroItem> {
    val officialBanner = homePage.banner?.let { banner ->
        HomeHeroItem(
            imageUrl = banner.picUrl,
            title = banner.title,
            subtitle = banner.description,
            videoCode = banner.videoCode,
        )
    }
    val videos = categories
        .sortedBy { if (it.key == HOME_CATEGORY_WATCHING_NOW) 0 else 1 }
        .flatMap { it.videos }
        .asSequence()
        .filter { it.videoCode != officialBanner?.videoCode }
        .distinctBy { it.videoCode }
        .take(HERO_MAX_ITEM_COUNT)
        .map { video ->
            HomeHeroItem(
                imageUrl = video.coverUrl,
                title = video.title,
                subtitle = subtitleFormatter(video.currentArtist, video.views, video.uploadTime),
                videoCode = video.videoCode,
            )
        }
        .toList()
    return (listOfNotNull(officialBanner) + videos).take(HERO_MAX_ITEM_COUNT)
}

private fun defaultHeroSubtitle(artist: String?, views: String?, time: String?): String? =
    listOfNotNull(
        artist?.takeIf { it.isNotBlank() },
        views?.takeIf { it.isNotBlank() }?.let(DisplayTextLocalizer::localizeViews),
        time?.takeIf { it.isNotBlank() }?.let(DisplayTextLocalizer::localizeRelativeTime),
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

/**
 * 将首页原始数据转换为 UI 可直接展示的分类行数据。
 *
 * @param homePage 仓库层返回的首页原始数据。
 * @return 当前站点类型下存在视频内容的分类行列表。
 */
fun buildCategoryList(homePage: HomePage, isAVSite: Boolean): List<HomeCategory> {
    return listOfNotNull(
        HomeCategory(
            key = HOME_CATEGORY_LATEST_HANIME,
            titleRes = if (isAVSite) Res.string.latest_av else Res.string.latest_hanime,
            genre = if (isAVSite) "日本AV" else "裏番",
            videos = homePage.ecchiAnime
        ),
        HomeCategory(
            key = HOME_CATEGORY_LATEST_RELEASE,
            titleRes = Res.string.latest_release,
            sort = "最新上市",
            videos = homePage.latestRelease
        ),
        HomeCategory(
            key = HOME_CATEGORY_LATEST_UPLOAD,
            titleRes = Res.string.latest_upload,
            sort = "最新上傳",
            videos = homePage.latestHanime
        ),
        HomeCategory(
            key = HOME_CATEGORY_WATCHING_NOW,
            titleRes = Res.string.they_watched,
            sort = "他們在看",
            videos = homePage.watchingNow
        ),
        HomeCategory(
            key = HOME_CATEGORY_SHORT_EPISODE,
            titleRes = if (isAVSite) Res.string.amateur_nomask else Res.string.category_instant_noodle,
            genre = if (isAVSite) "素人業餘" else "泡麵番",
            sort = "最新上傳",
            videos = homePage.shortEpisodeAnime
        ),
        HomeCategory(
            key = HOME_CATEGORY_MOTION_ANIME,
            titleRes = if (isAVSite) Res.string.hd_uncensored else Res.string.category_motion_anime,
            genre = if (isAVSite) "高清無碼" else "Motion Anime",
            sort = "最新上傳",
            videos = homePage.motionAnime
        ),
        HomeCategory(
            key = HOME_CATEGORY_3D_CG,
            titleRes = if (isAVSite) Res.string.ai_decensored else Res.string.category_3d_animation,
            genre = if (isAVSite) "AI解碼" else "3DCG",
            sort = "最新上傳",
            videos = homePage.threeDCG
        ),
        HomeCategory(
            key = HOME_CATEGORY_2_5D,
            titleRes = if (isAVSite) Res.string.china_av else Res.string.animation_2_5d,
            genre = if (isAVSite) "國產AV" else "2.5D",
            sort = "最新上傳",
            videos = homePage.twoPointFiveDAnime
        ),
        HomeCategory(
            key = HOME_CATEGORY_2D_ANIME,
            titleRes = if (isAVSite) Res.string.chinese_amateur else Res.string.animation_2d,
            genre = if (isAVSite) "國產素人" else "2D動畫",
            sort = "最新上傳",
            videos = homePage.twoDAnime
        ),
        HomeCategory(
            key = HOME_CATEGORY_AI_GENERATED,
            titleRes = if (isAVSite) Res.string.chinese_subtitle else Res.string.ai_generated,
            genre = if (isAVSite) null else "AI生成",
            tags = if (isAVSite) "中文字幕" else null,
            sort = "最新上傳",
            videos = homePage.aiGenerated
        ),
        HomeCategory(
            key = HOME_CATEGORY_MMD,
            titleRes = if (isAVSite) Res.string.ranking_today else Res.string.mmd,
            genre = if (isAVSite) null else "MMD",
            sort = if (isAVSite) "本日排行" else "最新上傳",
            videos = homePage.mmd
        ),
        HomeCategory(
            key = HOME_CATEGORY_COSPLAY,
            titleRes = if (isAVSite) Res.string.ranking_this_month else Res.string.category_cosplay,
            genre = if (isAVSite) null else "Cosplay",
            sort = if (isAVSite) "本月排行" else "最新上傳",
            videos = homePage.cosplay
        )
    ).filter { it.videos.isNotEmpty() }
        .let { categories ->
            val hiddenKeys = hiddenHomeCategoryKeys
            val orderIndex = homeCategoryOrder.withIndex().associate { it.value to it.index }
            categories
                .filterNot { it.key in hiddenKeys }
                .sortedBy { orderIndex[it.key] ?: Int.MAX_VALUE }
        }
}
