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
import lovehan1me.data.database.entity.SearchHistoryEntity
import lovehan1me.core.platform.ioDispatcher
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.SearchFilterSnapshot
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.core.domain.model.SearchOption.Companion.flatten
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.util.decodeComposeAsset
import lovehan1me.core.util.unsafeLazy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2022/06/13 013 22:29
 */
// P6c：SavedStateHandle（androidx）不可下沉 commonMain；nav3 @Serializable 路由不依赖它做参数传递，
// 原 state 持久化（进程死亡恢复查询/滚动）改普通属性，持久化接入推迟 P6d 导航层（债务记录）。
class SearchViewModel() : ViewModel() {

    var page: Int = 1
    var query: String? = null
    var genre: String? = null
    var sort: String? = null
    var year: Int? = null
    var month: Int? = null
    var approxTime: String? = null
    var broad: Boolean = false
    var duration: String? = null

    var gridFirstVisibleItemIndex: Int = 0
    var gridFirstVisibleItemScrollOffset: Int = 0

    // P6c：SparseArray → MutableMap（key 仅作占位/分组 id，见 sheet groupSelectedTagOptions）
    // P6d-3-C4：tagMap key 改 String（scope 名；原 titleRes Int 已改为 StringResource）
    var tagMap = mutableMapOf<String, Set<SearchOption>>()
    var brandMap = mutableMapOf<Int, Set<SearchOption>>()

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

    /**
     * 筛选态版本号。
     *
     * 上面这些筛选字段都是普通 `var`（不是 Compose State），所以**跨 composable 的写入不会
     * 触发重组**：宽屏下右侧结果区的「已生效条件」chip 把日期清掉后，左侧常驻筛选栏仍显示
     * 旧的「1990 年 / 已选 2 项」（2026-09-16 实测）。
     *
     * 约定：凡是**从筛选面板之外**改动筛选态的方法，改完都要 [bumpFilterRevision]；
     * 常驻栏把本值并进重组 key 即可重新读取。面板内部的改动走它自己的 selectionVersion。
     */
    var filterRevision by mutableIntStateOf(0)
        private set

    fun bumpFilterRevision() {
        filterRevision++
    }

    private val _searchStateFlow =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchStateFlow = _searchStateFlow.asStateFlow()

    private val _searchFlow = MutableStateFlow(emptyList<HanimeInfo>())
    val searchFlow = _searchFlow.asStateFlow()

    fun clearHanimeSearchResult() {
        _searchFlow.value = emptyList()
        _searchStateFlow.value = PageLoadingState.Loading
    }

    fun resetSearchUiState() {
        page = 1
        query = null
        genre = null
        sort = null
        year = null
        month = null
        approxTime = null
        broad = false
        duration = null
        tagMap.clear()
        brandMap.clear()
        gridFirstVisibleItemIndex = 0
        gridFirstVisibleItemScrollOffset = 0
        _searchFlow.value = emptyList()
        _searchStateFlow.value = PageLoadingState.Loading
        bumpFilterRevision()
    }

    fun getHanimeSearchResult(
        page: Int, query: String?, genre: String?,
        sort: String?, broad: Boolean, date: String?,
        duration: String?, tags: Set<String>, brands: Set<String>,
    ) {
        viewModelScope.launch {
            NetworkRepo.getHanimeSearchResult(
                page, query, genre,
                sort, broad, date ,
                duration, tags, brands
            ).collect { state ->
                val prev = _searchStateFlow.getAndUpdate { state }
                if (prev is PageLoadingState.Loading) _searchFlow.value = emptyList()
                _searchFlow.update { prevList ->
                    when (state) {
//                        is PageLoadingState.Success -> prevList + state.info
                        is PageLoadingState.Success -> {
                            val list = state.info
                            val updatedList = if (SettingsRepository.showPlayedIndicator) {
                                val codes = list.map { it.videoCode }
                                val watchedCodes = withContext(ioDispatcher) {
                                    DatabaseRepo.WatchHistory.getWatched(codes).toSet()
                                }
                                list.map { item ->
                                    item.copy(watched = watchedCodes.contains(item.videoCode))
                                }
                            } else {
                                list
                            }
                            (prevList + updatedList).distinctBy(HanimeInfo::videoCode)
                        }
                        is PageLoadingState.Loading -> emptyList()
                        else -> prevList
                    }
                }
            }
        }
    }

    fun insertSearchHistory(history: SearchHistoryEntity) {
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
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
                return@launch
            }
            LogUtil.i("insertAdvancedSearchHistory","记录重复！")

        }
    }

    fun deleteSearchHistory(history: SearchHistoryEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.SearchHistory.delete(history)
            LogUtil.d("delete_search_hty", "$history DONE!")
        }
    }

    fun loadAllSearchHistories(keyword: String? = null) =
        DatabaseRepo.SearchHistory.loadAll(keyword).flowOn(ioDispatcher)

    fun deleteSearchHistoryByKeyword(query: String) {
        viewModelScope.launch(ioDispatcher) {
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

            tagMap.clear()
            brandMap.clear()

            snapshot.tags?.takeIf { it.isNotBlank() }?.let { tagsString ->
                val tagOptions = tagsString.toSearchOptionSet()
                tagMap.put("history", tagOptions)
            }

            snapshot.brands?.takeIf { it.isNotBlank() }?.let { brandsString ->
                val brandOptions = brandsString.toSearchOptionSet()
                brandMap.put(0, brandOptions)
            }
            bumpFilterRevision()
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
