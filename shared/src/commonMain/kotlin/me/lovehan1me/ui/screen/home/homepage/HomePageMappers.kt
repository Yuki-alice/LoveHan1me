package me.lovehan1me.ui.screen.home.homepage

import me.lovehan1me.Res
import me.lovehan1me.ai_decensored
import me.lovehan1me.ai_generated
import me.lovehan1me.amateur_nomask
import me.lovehan1me.animation_2_5d
import me.lovehan1me.animation_2d
import me.lovehan1me.category_3d_animation
import me.lovehan1me.category_cosplay
import me.lovehan1me.category_instant_noodle
import me.lovehan1me.category_motion_anime
import me.lovehan1me.china_av
import me.lovehan1me.chinese_amateur
import me.lovehan1me.chinese_subtitle
import me.lovehan1me.hd_uncensored
import me.lovehan1me.latest_av
import me.lovehan1me.latest_hanime
import me.lovehan1me.latest_release
import me.lovehan1me.latest_upload
import me.lovehan1me.logic.model.HomePage
import me.lovehan1me.mmd
import me.lovehan1me.ranking_this_month
import me.lovehan1me.ranking_today
import me.lovehan1me.they_watched

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
