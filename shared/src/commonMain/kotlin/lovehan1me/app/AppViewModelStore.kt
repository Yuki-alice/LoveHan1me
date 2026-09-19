package lovehan1me.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import lovehan1me.feature.home.homepage.HomePageViewModel

/**
 * 「当前一代」的 ViewModelStore —— 数据源热切换（软重启）的支点。
 *
 * ---
 *
 * ### 为什么需要它跨 Composable 暴露
 *
 * `App()` 里用 `key(generation)` 重建整棵 composition，但 **ViewModel 不在
 * composition 里** —— 它们住在 `ViewModelStoreOwner` 的 store 中（Android =
 * `ComponentActivity`），光重建 UI 清不掉旧站的 VM（残留 `homePageFlow` 里的旧站数据、
 * 旧的回退栈）。
 *
 * 所以热切换必须**换一代 store**。而 store 不能只换在 `App()` 的 CompositionLocal 里：
 * Android 的 `MainActivity` 也要用**同一个** `HomePageViewModel`（intent 深链、返回键、
 * 头像搜索跳转都直接操作 `mainBackStack`）。若 Activity 侧仍拿旧一代的 VM，切换后
 * 这些入口会压到一个已经不在界面上的栈里 —— 表现为"点了没反应"。
 *
 * 因此把"当前代次"提成进程级单点：`App()` 与 `MainActivity` 都从这里取。
 *
 * ### 与路由级 VM 的关系
 *
 * 只有「App 级」VM 走这里。`SearchViewModel` / `VideoViewModel` 等挂在 `NavDisplay` 的
 * `rememberViewModelStoreNavEntryDecorator()` 提供的按条目 store 上，会随导航栈
 * 重置自动销毁，不需要也不该由本容器接管。
 */
class SiteScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

object AppViewModelStore {

    @Volatile
    private var owner: ViewModelStoreOwner = SiteScopedViewModelStoreOwner()

    /**
     * [owner] 当前对应的 [lovehan1me.site.SiteSwitcher.generation]。
     *
     * 初值与 `SiteSwitcher` 的初始代次一致（都是 0）—— 这样首帧组合不会白白旋转一次，
     * 也就不会误伤 Activity 在组合之前就已取到的 VM（`MainActivity.mainBackStack`
     * 在 `onActivityCreated` 就可能被深链入口读到）。
     */
    @Volatile
    private var generation: Int = 0

    /** 当前代次的 store owner。`App()` 把它提供给 `LocalViewModelStoreOwner`。 */
    val current: ViewModelStoreOwner get() = owner

    /**
     * 把 store 对齐到 [generation]：代次前进了就换一代，否则原样返回。
     *
     * **幂等 + 单调**，这两点都不是洁癖：
     * - 幂等：本方法在 `App()` 的 `remember(generation)` 块里被调用，而 `remember`
     *   的计算块在组合被取消后**会重跑**。没有相等判断就会在同一代次里旋转两次，
     *   第二次把刚建好的 VM 清掉 —— 表现为"切完站首页空白、要点一下才回来"。
     * - 单调：`generation <= this.generation` 一律不动作，避免任何调用顺序异常
     *   把已经换代的新 store 又清回去。
     *
     * 旧 store 先换出、再 `clear()`：`clear()` 会触发其中 VM 的 `onCleared`，
     * 万一有回调重入本对象，此时读到的已是新一代，不会连锁误清。
     */
    fun alignTo(generation: Int): ViewModelStoreOwner {
        if (generation <= this.generation) return owner
        val previous = owner
        this.generation = generation
        owner = SiteScopedViewModelStoreOwner()
        previous.viewModelStore.clear()
        return owner
    }

    /**
     * 取当前代次的 [HomePageViewModel]。
     *
     * 与 `App()` 里 `sharedViewModel(::HomePageViewModel)` 拿到的是**同一个实例** ——
     * 两者都从 [current] 取，且都用默认 key（`ViewModelProvider.DEFAULT_KEY +
     * ":" + canonicalName`），store 按 key 命中缓存返回同一对象。
     */
    fun homePageViewModel(): HomePageViewModel =
        ViewModelProvider.create(current, HomePageViewModelFactory).get(HomePageViewModel::class)
}

/**
 * `HomePageViewModel` 的构造器工厂。
 *
 * 用官方 `viewModelFactory { initializer {} }` DSL 而不是手写 `ViewModelProvider.Factory`
 * 实现：本版本（KMP lifecycle 2.11.0）的 `Factory` 接口**只有**
 * `create(modelClass: KClass<T>, extras: CreationExtras)` 一个成员，老的
 * `create(Class<T>)`（Java 形状）已不存在；且裸 `viewModel()` 在桌面/iOS 会因
 * `SavedStateViewModelFactory(nonAndroid)` 抛 `UnsupportedOperationException`（见
 * `SharedViewModel.kt`），DSL 由 builder 自行适配各平台 Factory 形状，是唯一稳妥写法。
 */
private val HomePageViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory { initializer { HomePageViewModel() } }
