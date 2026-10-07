package lovehan1me.core.domain.model

import androidx.compose.runtime.Immutable
import lovehan1me.site.hanime1.ResolutionLinkMap
import lovehan1me.core.util.mapToArray
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 视频详情模型。**可变性是显式契约，勿轻改。**
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/11 011 20:30
 *
 * 关于 [Immutable]：
 * 本类声明为 `@Immutable`，即向 Compose 编译器承诺「实例一旦构造，其所有对外可读的
 * 属性在生命周期内永不变更」，从而使本类在组合中可被跳过（skippable）。这条承诺成立
 * 的前提是**全类字段皆为 `val` 且其声明类型不可变**：
 *
 *  - 直接字段全部为 `val`（本文件已逐行确认）；
 *  - [MyList.isWatchLater] / [MyList.MyListInfo.isSelected] 已由 `var` 收敛为 `val`，
 *    其写入点已实测清零 —— 全仓不存在带接收者的直接赋值（`.isWatchLater = …` /
 *    `.isSelected = …`），写入仅发生在**构造实参**与 `copy(...)` 实参中，二者对 `val`
 *    同样生效；
 *  - [videoUrls] 的类型由具体可变类 `LinkedHashMap` 收敛为只读的 [Map]。
 *
 * ⚠️ 日后若有人为本类新增任何 `var` 字段、或把一个 `MutableXxx` 类型塞进字段声明，
 * **必须先回来修改这里并移除 [Immutable]**，否则该注解就从「契约」退化成「谎言」：
 * 编译器会基于错误承诺做跳过优化，界面将出现「数据变了但不重组」的幽灵 bug。
 */
@Serializable
@Immutable
data class HanimeVideo(
    val title: String,
    val coverUrl: String,
    val chineseTitle: String?,
    val introduction: String?,
    val uploadTime: LocalDate?,
    @Transient val views: String? = null,

    // resolution to video url
    val videoUrls: ResolutionLinkMap,

    val tags: List<String>,
    /**
     * 注意，這裏的myList是指用戶的播放清單playlist
     */
    @Transient val myList: MyList? = null,
    /**
     * 注意，這裏的playlist是指該影片的系列影片，並非用戶的播放清單
     */
    @Transient val playlist: Playlist? = null,
    @Transient val relatedHanimes: List<HanimeInfo> = emptyList(),
    val artist: Artist? = null,

    @Transient val favTimes: Int? = null,
    @Transient val isFav: Boolean = false,
    @Transient val unlikesCount: Int? = null,
    @Transient val isUnlike: Boolean = false,
    @Transient val csrfToken: String? = null,
    @Transient val currentUserId: String? = null,
    @Transient val originalComic: String? = null,
) {

    val ratingCount: Int?
        get() = if (favTimes != null || unlikesCount != null) {
            (favTimes ?: 0) + (unlikesCount ?: 0)
        } else {
            null
        }

    val likeRatio: Int?
        get() = ratingCount?.takeIf { it > 0 }?.let { total ->
            (((favTimes ?: 0) * 100f) / total).toInt()
        }

    /**
     * 展示用主标题：优先中文标题，退回站内原标题。
     *
     * 播放器顶栏与右栏简介第一行**共用这一条**。两处各写一遍这段判断，
     * 迟早会漂成"顶栏一个名字、简介另一个名字"。
     */
    val primaryTitle: String
        get() = chineseTitle?.takeIf { it.isNotBlank() } ?: title

    fun rateVideo(isPositive: Boolean): HanimeVideo {
        val liked = isFav
        val unliked = isUnlike
        val likes = favTimes ?: 0
        val unlikes = unlikesCount ?: 0
        return when {
            isPositive && liked -> copy(favTimes = (likes - 1).coerceAtLeast(0), isFav = false)
            isPositive -> copy(
                favTimes = likes + 1,
                unlikesCount = if (unliked) (unlikes - 1).coerceAtLeast(0) else unlikesCount,
                isFav = true,
                isUnlike = false,
            )

            !isPositive && unliked -> copy(
                unlikesCount = (unlikes - 1).coerceAtLeast(0),
                isUnlike = false,
            )

            else -> copy(
                favTimes = if (liked) (likes - 1).coerceAtLeast(0) else favTimes,
                unlikesCount = unlikes + 1,
                isFav = false,
                isUnlike = true,
            )
        }
    }

    // 為保證兼容性，不能直接用天數
    val uploadTimeMillis: Long
        get() = uploadTime?.let {
            it.toEpochDays() * 24 * 60 * 60 * 1000
        } ?: 0L

    data class MyList(
        val isWatchLater: Boolean,
        val myListInfo: List<MyListInfo>,
    ) {
        data class MyListInfo(
            val code: String,
            val title: String,
            val isSelected: Boolean,
        )

        val titleArray get() = myListInfo.mapToArray(MyListInfo::title)
    }

    data class Playlist(
        val playlistName: String?,
        val video: List<HanimeInfo>,
        /** G2-1b-1：`#playlist-top-block h4 a` 的 href（独立 `/playlist` 页，G2-1b-2 用）。 */
        val listUrl: String? = null,
    )

    @Serializable
    data class Artist(
        val name: String,
        val avatarUrl: String,
        val genre: String,
        @Transient val post: POST? = null,
    ) {
        val isSubscribed: Boolean get() = post != null && post.isSubscribed

        data class POST(
            val userId: String,
            val artistId: String,
            val isSubscribed: Boolean,
        )
    }
}
