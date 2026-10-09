package lovehan1me.feature.home.homepage

import androidx.compose.runtime.staticCompositionLocalOf
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HomePage
import org.jetbrains.compose.resources.StringResource

/**
 * 首页主要数据源
 * @param page 主页主要数据
 *
 * 原先还有个 `announcements` 字段，但**从没有人往里写过** ——
 * `HomePageViewModel` 构造它时是 `HomeData(page = networkState.info)`，
 * 上游 `Han1meViewer` 的同一行也一样，于是首页那段公告分支永远不成立、
 * 关闭公告的回调也永远不可达。公告现已独立成
 * `HomePageViewModel.announcements`（来源与刷新时机都和首页内容不同），
 * 故此处删掉该字段而不是补一个假数据源。
 */
data class HomeData(
    val page: HomePage,
)

/**
 * 为首页搜索相关组件提供搜索历史查询能力。
 */
val LocalSearchHistoryQuery = staticCompositionLocalOf<suspend (String) -> List<String>> {
    { emptyList() }
}

/**
 * 首页 Hero 轮播项统一模型（官网运营位 + 视频条目混排）。
 *
 * 来源见 `buildHomeHeroItems`：官网只下发一条运营位时轮播无法翻页，
 * 故从下方分类行取视频补足。`subtitle` 对运营位是站下发的描述，
 * 对视频是「作者 · 观看数 · 时间」合成（已本地化，缺段自动省略）。
 */
data class HomeHeroItem(
    val imageUrl: String,
    val title: String,
    val subtitle: String?,
    val videoCode: String?,
)

/**
 * 首页视频分类行数据。
 *
 * @param titleRes 分类标题的字符串资源。
 * @param genre 高级搜索使用的可选类型参数。
 * @param sort 高级搜索使用的可选排序参数。
 * @param tags 高级搜索使用的可选标签参数。
 * @param videos 当前分类下展示的视频列表。
 */
data class HomeCategory(
    val key: String,
    val titleRes: StringResource,
    val genre: String? = null,
    val sort: String? = null,
    val tags: String? = null,
    val videos: List<HanimeInfo>
)
