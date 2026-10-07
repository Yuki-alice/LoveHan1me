package lovehan1me.feature.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 本地（免登录）MyList 分支：[LocalFavSubViewModel] / [LocalWatchLaterSubViewModel]。
 *
 * 这两个实现把整份本地列表一次性加载，没有分页，因此 `loadNextPage()` 恒为 `false`。
 *
 * ## 覆盖边界（诚实说明）
 * 本用例只覆盖**不触碰数据源**的部分：构造、[MyListPagingController.loadNextPage]、
 * [MyListPagingController.clearMyListItems]。`refresh()` 的"清空重载 / 替换语义"需要
 * `LocalListRepository.observeFavorites()/observeWatchLater()`（背后是 Room 单例
 * `Han1meDatabases.localList`），要确定性测它就得上 fake DAO、或给仓库开注入缝——
 * 本轮**不做**（避免为测试改产品代码），故 `refresh()` 仍由"与旧版逐句等价"保证。
 */
class LocalMyListBranchTest {

    @Test
    fun `本地分支无分页且重置后归零`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val controllers: List<MyListPagingController> = listOf(
            LocalFavSubViewModel(scope),
            LocalWatchLaterSubViewModel(scope),
        )

        controllers.forEach { controller ->
            assertFalse(
                controller.loadNextPage(),
                "本地列表没有分页：loadNextPage() 必须恒为 false" +
                    "（实现=${controller::class.simpleName}）",
            )
            assertEquals(0, controller.loadedPageCount.value, "本地列表初始不应有已加载页数")
            assertFalse(controller.isLoadingMore.value, "本地列表初始不应处于加载更多态")

            // 重置路径（闸门之外的合法写入）：不应触碰数据源，且状态归零。
            controller.clearMyListItems()
            assertEquals(0, controller.loadedPageCount.value, "重置后已加载页数应归零")
            assertFalse(controller.isLoadingMore.value, "重置后不应处于加载更多态")
        }
    }
}
