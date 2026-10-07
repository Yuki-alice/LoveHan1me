package lovehan1me.feature.search

import lovehan1me.core.util.LogUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.site.SiteIdentity
import lovehan1me.core.constant.HanimeConstants.HANIME_URL
import lovehan1me.data.SettingsRepository
import lovehan1me.site.hanime1.HanimeAdvancedSearchRepo
import lovehan1me.site.hanime1.HanimeAdvancedSearchRepo.toDbString
import lovehan1me.site.hanime1.HanimeAdvancedSearchRepo.toSearchOptionSet
import lovehan1me.data.DatabaseRepo
import lovehan1me.data.NetworkRepo
import lovehan1me.data.discoverCacheKey
import lovehan1me.data.readCachedDiscoverHtml
import lovehan1me.site.hanime1.Parser
import lovehan1me.data.database.entity.SearchHistoryEntity
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.SearchFilterSnapshot
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.model.SearchOption.Companion.flatten
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.PagingGate
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.unsafeLazy
import lovehan1me.ui.foundation.launchSafely
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/13 013 22:29
 */

/**
 * 搜索结果常驻上限（B3）。
 *
 * `_searchFlow` 原本无界累加：每翻一页就把新页并进旧列表、再对**整表** `distinctBy`，
 * 于是常驻内存随页数线性上涨，且单页成本也随累计量涨（翻到第 N 页时累计成本是平方级）。
 * 封顶后常驻内存封顶、单页成本恒定（上限 + 一页）。
 *
 * 取舍（有意为之，文档已记）：超出上限后**丢弃最早的一批**，用户往上翻会看不到最初几页。
 * 这是「封顶 vs 切 Paging3」里选前者的既定代价——后者改动量高一个量级。
 */
internal const val SEARCH_RESULT_LIMIT = 500

/**
 * 把新一页并入已有结果：先按 `videoCode` 去重，再按 [limit] 封顶（保最新）。
 *
 * 抽成纯函数是为了可测：ViewModel 依赖 DB/网络，headless 下起不来；
 * 而"去重 + 封顶"这段正是 B3 的全部语义，单独钉住即可（同 `PlaybackUiStateTest` 的路子）。
 */
internal fun mergeSearchPage(
    prev: List<HanimeInfo>,
    incoming: List<HanimeInfo>,
    limit: Int = SEARCH_RESULT_LIMIT,
): List<HanimeInfo> =
    (prev + incoming).distinctBy(HanimeInfo::videoCode).takeLast(limit)

// P6c：SavedStateHandle（androidx）不可下沉 commonMain；nav3 @Serializable 路由不依赖它做参数传递，
// 原 state 持久化（进程死亡恢复查询/滚动）改普通属性，持久化接入推迟 P6d 导航层（债务记录）。
class SearchViewModel() : ViewModel() {

    /** 分页序号：纯命令式计数，不参与 UI 展示，故保持普通属性。 */
    var page: Int = 1

    // 筛选态：Compose State（2026-10-07 重构，原为普通 var + filterRevision 手动版本号约定）。
    // 普通 var 在组合期读取不会触发重组，所以此前要靠"谁改了谁记得 ++ 版本号"这种脆弱约定；
    // 改成 State 后读写即自动建立依赖，跨 composable 的写入也能立刻反映到常驻筛选栏。
    var query: String? by mutableStateOf(null)
    var genre: String? by mutableStateOf(null)
    var sort: String? by mutableStateOf(null)
    var year: Int? by mutableStateOf(null)
    var month: Int? by mutableStateOf(null)
    var approxTime: String? by mutableStateOf(null)
    var broad: Boolean by mutableStateOf(false)
    var duration: String? by mutableStateOf(null)

    /** 网格滚动位置：由 snapshotFlow 高频写入，作普通属性即可（不被组合期读取）。 */
    var gridFirstVisibleItemIndex: Int = 0
    var gridFirstVisibleItemScrollOffset: Int = 0

    // P6c：SparseArray → Map（key 仅作占位/分组 id，见 sheet groupSelectedTagOptions）
    // P6d-3-C4：tagMap key 改 String（scope 名；原 titleRes Int 已改为 StringResource）
    // 用不可变 Map + 整体替换：State 对原地 clear()/put() 不可见，只有换引用才触发重组。
    var tagMap by mutableStateOf<Map<String, Set<SearchOption>>>(emptyMap())
    var brandMap by mutableStateOf<Map<Int, Set<SearchOption>>>(emptyMap())

    val genres by unsafeLazy {
        decodeComposeAsset<List<SearchOption>>(if (SiteIdentity.isAvSite) "files/search_options/genre_av.json" else "files/search_options/genre.json").orEmpty()
    }

    val tags by unsafeLazy {
        decodeComposeAsset<Map<String, List<SearchOption>>>("files/search_options/tags.json").orEmpty()
    }

    val brands by unsafeLazy {
        decodeComposeAsset<List<SearchOption>>("files/search_options/brands.json").orEmpty()
    }

    val sortOptions by unsafeLazy {
        decodeComposeAsset<List<SearchOption>>("files/search_options/sort_option.json").orEmpty()
    }

    val durations by unsafeLazy {
        decodeComposeAsset<List<SearchOption>>("files/search_options/duration.json").orEmpty()
    }
    val timeList by unsafeLazy {
        decodeComposeAsset<List<SearchOption>>("files/search_options/release_date.json").orEmpty()
    }

    private val _searchStateFlow =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchStateFlow = _searchStateFlow.asStateFlow()

    private val _searchFlow = MutableStateFlow(emptyList<HanimeInfo>())
    val searchFlow = _searchFlow.asStateFlow()

    /**
     * 分页闸门：判重 / 请求标识 / 作废，见 [PagingGate]。
     *
     * C 类修复的支点：**分页序列的唯一所有者**从 UI 回调挪回这里。此前
     * `SearchScreen` 的 `onLoadMore` 是 `{ viewModel.page++; executeSearch() }`，
     * 快速滑动可被连续触发，`page` 被连加、多个请求同时在途，且响应无差别并入列表。
     */
    private val pagingGate = PagingGate()

    fun clearHanimeSearchResult() {
        // 清屏也要作废在途请求：否则一个已经在路上的旧请求会在清屏之后落回来，
        // 把刚清掉的列表又填上（这正是"UI 自增 page + 无判重"那套的表现之一）。
        pagingGate.cancel()
        _searchFlow.value = emptyList()
        _searchStateFlow.value = PageLoadingState.Loading
    }

    fun resetSearchUiState() {
        // 同 clearHanimeSearchResult：态清空就必须同时作废在途请求，否则旧响应会落回来。
        pagingGate.cancel()
        page = 1
        query = null
        genre = null
        sort = null
        year = null
        month = null
        approxTime = null
        broad = false
        duration = null
        tagMap = emptyMap()
        brandMap = emptyMap()
        gridFirstVisibleItemIndex = 0
        gridFirstVisibleItemScrollOffset = 0
        _searchFlow.value = emptyList()
        _searchStateFlow.value = PageLoadingState.Loading
    }

    /**
     * 第 1 页加载入口：新查询 / 改筛选 / 下拉刷新 / 首次进入。
     *
     * 先 [PagingGate.restart] 作废在途请求 —— 刷新必须**能打断**在途请求，
     * 不能被判重挡死，否则用户下拉时如果正好有翻页在跑，刷新就静默失效了。
     *
     * 顺带把 `page` 归 1 收进 VM：UI 不再持有分页序号。
     */
    fun startFirstPage() {
        page = 1
        launchSearch(page = 1, token = pagingGate.restart())
    }

    /**
     * 加载下一页。**分页序列推进（`page` 自增 + 判重）全部收在这里。**
     *
     * @return 本次触发是否真的发起了加载；false = 已有在途请求，本次触发被判重丢弃。
     *   UI 不需要看这个返回值（它只管调用），返回它只为让行为可断言。
     */
    fun loadNextPage(): Boolean {
        // 判重：在途时直接丢弃本次触发。这就是"快速滑动只发一个请求"的那道闸。
        val token = pagingGate.tryBegin() ?: return false
        page += 1
        launchSearch(page = page, token = token)
        return true
    }

    /**
     * 真正发起一次加载。筛选参数从本 VM 字段现取（原先由 UI 逐项转发，等价）。
     *
     * [token] 是本次加载的凭证：**响应回来先过 [PagingGate.isCurrent]**，
     * 过期就直接丢弃 —— 不写 `_searchFlow`、不写 `_searchStateFlow`，
     * 也就永远不会走到 [mergeSearchPage]。
     */
    private fun launchSearch(page: Int, token: PagingGate.Token) {
        val tags = tagFlatten(tagMap)
        val brands = brandFlatten(brandMap)
        val date = getSearchDate()
        // 发现页默认浏览（空搜 page=1、无任何筛选）走 stale 缓存：
        // key 本来就不含筛选参数，有条件的请求天然不可缓存，不会串结果。
        val cacheable = page == 1 && query.isNullOrBlank() && genre == null &&
                sort == null && !broad && date == null && duration == null &&
                tags.isEmpty() && brands.isEmpty()
        launchSafely("SearchViewModel.launchSearch") {
            try {
                // 先展陈旧第一页（~0.2s 解析），再正常拉新覆盖 —— 进 tab 不再白等整轮网络。
                // 刷新行为不变（永远拉新）；无缓存/解析失败就当 miss。
                var staleReplaced = false
                if (cacheable) {
                    val cached = runCatching {
                        withContext(ioDispatcher) {
                            readCachedDiscoverHtml(discoverCacheKey())?.let(Parser::hanimeSearch)
                        }
                    }.getOrNull()
                    // 读缓存是挂起的：期间可能已被 cancel/restart 作废，故同样要过闸门。
                    if (cached is PageLoadingState.Success && pagingGate.isCurrent(token)) {
                        _searchStateFlow.value = cached
                        _searchFlow.value = withWatched(cached.info)
                        staleReplaced = true
                    }
                }
                NetworkRepo.getHanimeSearchResult(
                    page, query, genre,
                    sort, broad, date,
                    duration, tags, brands,
                    writeCache = cacheable,
                ).collect { state ->
                    // 闸门：作废过的响应直接丢弃。不写流、不并列表，也不碰闸门
                    // （新一轮已经在跑，finish 交给 finally，且它对过期凭证是 no-op）。
                    if (!pagingGate.isCurrent(token)) return@collect
                    val prev = _searchStateFlow.getAndUpdate { state }
                    if (prev is PageLoadingState.Loading) _searchFlow.value = emptyList()
                    _searchFlow.update { prevList ->
                        when (state) {
                            is PageLoadingState.Success -> {
                                val updatedList = withWatched(state.info)
                                // 陈旧页被新鲜第一页替换（不是追加）：去重保首项，
                                // 直接追加会把旧快照顶在前面、顺序错乱。
                                val base =
                                    if (staleReplaced) { staleReplaced = false; emptyList() } else prevList
                                // B3：去重 + 封顶（超出上限丢最早的，见 SEARCH_RESULT_LIMIT 注释）。
                                mergeSearchPage(base, updatedList)
                            }
                            is PageLoadingState.Loading -> emptyList()
                            else -> prevList
                        }
                    }
                }
            } finally {
                // 放在 finally：请求异常 / 协程被取消也要释放闸门，否则翻页会永久卡死。
                // 对已被作废的凭证是 no-op（不会误释放新一轮的闸门）。
                pagingGate.finish(token)
            }
        }
    }

    /** 已看标记：原来内联在收集分支里，缓存首展与网络刷新共用。 */
    private suspend fun withWatched(list: List<HanimeInfo>): List<HanimeInfo> {
        if (!SettingsRepository.showPlayedIndicator) return list
        val watchedCodes = withContext(ioDispatcher) {
            DatabaseRepo.WatchHistory.getWatched(list.map { it.videoCode }).toSet()
        }
        return list.map { item ->
            item.copy(watched = watchedCodes.contains(item.videoCode))
        }
    }

    fun insertSearchHistory(history: SearchHistoryEntity) {
        launchSafely("SearchViewModel.insertSearchHistory", ioDispatcher) {
            DatabaseRepo.SearchHistory.insert(history)
            LogUtil.d("insert_search_hty", "$history DONE!")
        }
    }

    fun getSearchDate(): String? {
        return when {
            approxTime != null -> approxTime
            year != null -> listOfNotNull(
                year?.let { "$it 年" },
                month?.let { "$it 月" }
            ).joinToString(" ")
            else -> null
        }
    }

    fun insertAdvancedSearchHistory(
        query: String?, genre: String?,
        sort: String?, broad: Boolean, date: String?,
        duration: String?, tags: Set<SearchOption>, brands: Set<SearchOption>,
    ) {
        launchSafely("SearchViewModel.insertAdvancedSearchHistory", ioDispatcher) {
            val histories = HanimeAdvancedSearchRepo.getSearchHistories(limit = 10)
                .first()

            val isDuplicate = histories.any { history ->
                history.query == query &&
                        history.genre == genre &&
                        history.sort == sort &&
                        history.broad == broad &&
                        history.date == date &&
                        history.duration == duration &&
                        history.tags?.toSearchOptionSet() == tags &&
                        history.brands?.toSearchOptionSet() == brands
            }

            if (!isDuplicate) {
                HanimeAdvancedSearchRepo.saveSearch(
                    query = query,
                    genre = genre,
                    sort = sort,
                    broad = broad,
                    date = date,
                    duration = duration,
                    tags = tags,
                    brands = brands,
                )
                return@launchSafely
            }
            LogUtil.i("insertAdvancedSearchHistory","记录重复！")

        }
    }

    fun deleteSearchHistory(history: SearchHistoryEntity) {
        launchSafely("SearchViewModel.deleteSearchHistory", ioDispatcher) {
            DatabaseRepo.SearchHistory.delete(history)
            LogUtil.d("delete_search_hty", "$history DONE!")
        }
    }

    fun loadAllSearchHistories(keyword: String? = null) =
        DatabaseRepo.SearchHistory.loadAll(keyword).flowOn(ioDispatcher)

    fun deleteSearchHistoryByKeyword(query: String) {
        launchSafely("SearchViewModel.deleteSearchHistoryByKeyword", ioDispatcher) {
            DatabaseRepo.SearchHistory.deleteByKeyword(query)
            LogUtil.d("delete_search_hty", "$query DONE!")
        }
    }
    val refreshTriggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun triggerNewSearch() {
        page = 1
        clearHanimeSearchResult()
        refreshTriggerFlow.tryEmit(Unit)
    }
    /**
     * 把当前筛选态导出成 [SearchFilterSnapshot]（保存命名预设用）。
     *
     * 刻意走与 `insertAdvancedSearchHistory` 完全相同的字段与串表示（日期用 [getSearchDate] 拼、
     * 标签/品牌用 `toDbString` 拼），这样 [restoreSearchMap] 对预设和历史的还原效果一致 ——
     * 不会出现"历史能还原、预设还原不出来"这种分叉。
     */
    fun currentFilterSnapshot(): SearchFilterSnapshot = SearchFilterSnapshot(
        query = query?.takeIf { it.isNotBlank() },
        genre = genre,
        sort = sort,
        broad = broad,
        date = getSearchDate(),
        duration = duration,
        tags = tagMap.toWireFilterString(),
        brands = brandMap.toWireFilterString(),
    )

    /** 选中项集合 → wire 层逗号串；空集合给 null（表示该维度无筛选）。 */
    private fun Map<*, Set<SearchOption>>.toWireFilterString(): String? =
        flatten().map { SearchOption(searchKey = it) }.toSet().toDbString().ifBlank { null }

    /**
     * 把一份筛选快照恢复进搜索态。
     *
     * 参数从 `HanimeAdvancedSearchHistoryEntity` 放宽成 [SearchFilterSnapshot]（2026-09-16）：
     * 高级搜索历史与命名预设都先归约成快照，于是"恢复"只有这一份实现。
     * 新增筛选维度时改两处转换（`toSnapshot()` / [currentFilterSnapshot]）即可。
     */
    fun restoreSearchMap(snapshot: SearchFilterSnapshot) {
        with(this) {
            page = 1
            query = snapshot.query
            genre = snapshot.genre
            sort = snapshot.sort
            broad = snapshot.broad
            duration = snapshot.duration

            restoreDate(this, snapshot.date)

            tagMap = snapshot.tags?.takeIf { it.isNotBlank() }?.let { tagsString ->
                mapOf("history" to tagsString.toSearchOptionSet())
            } ?: emptyMap()

            brandMap = snapshot.brands?.takeIf { it.isNotBlank() }?.let { brandsString ->
                mapOf(0 to brandsString.toSearchOptionSet())
            } ?: emptyMap()
        }
    }
    private fun restoreDate(viewModel: SearchViewModel, date: String?) {
        if (date.isNullOrBlank()) {
            viewModel.year = null
            viewModel.month = null
            viewModel.approxTime = null
            return
        }

        if (date.contains("過去")) {
            viewModel.approxTime = date
            viewModel.year = null
            viewModel.month = null
        } else {
            viewModel.approxTime = null
            val regex = """(\d+)\s*年(?:\s*(\d+)\s*月)?""".toRegex()
            val match = regex.find(date)
            if (match != null) {
                val (y, m) = match.destructured
                viewModel.year = y.toIntOrNull()
                viewModel.month = m.toIntOrNull()
            } else {
                viewModel.year = null
                viewModel.month = null
            }
        }
    }
}
