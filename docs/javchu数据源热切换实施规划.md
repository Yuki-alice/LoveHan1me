# javchu 数据源热切换：修复与实施规划

> 2026-09-17。前置取证见同目录 `javchu数据源切换梳理与闪退定位.md`（现状链路 / 与上游 12 项对比 / 桌面实测）。
> 本文回答两件事：**①「和上游一样」到底是什么；②怎么在不退进程的前提下做到它。**
> 全部结论均带 `文件:行号` 证据，可直接复核。

---

## 0. 基线纠偏：上游并没有热切换

需求原话是「实现 Han1meViewer 一样的热切换」。取证结论是：**上游是重启式切换，不存在热切换**。方案必须先立对靶子。

| 上游路径 | 实现 | 位置 |
| --- | --- | --- |
| 站点切换（AV ↔ 番剧） | `update{}` → `delay(500)` → `restart(killProcess = true)` | `references/.../MainActivity.kt:212-225` |
| 网域切换 | `update{}` → `logout()` → `restart(killProcess = true)`（**无 delay**） | `references/.../NetworkSettingsRoute.kt:344-345` |
| 重启实现 | `startActivity(launchIntent + CLEAR_TOP\|CLEAR_TASK\|NEW_TASK)` → `exitProcess(0)` | `references/.../utils/ActivityManager.kt:13-21` |
| 备份导入 | `activity.recreate()` | `references/.../HomeSettingsRoute.kt:334` |

**关键洞察（决定整个方案形状）**：上游那次「重启」清掉的是**进程内状态**；持久层 —— Room 4 个库、DataStore、OkHttp 磁盘缓存、下载文件 —— **一个都不清**（重启后还是同一份文件）。

于是：

1. 「和上游一样」的正确含义是 **切换后状态干净、无脏数据**，而不是「必须零重启」。
2. 热切换的正确定义 = **在进程内复现「重启会清掉的那部分状态」**，效果等价于上游重启，但不退进程、不闪退、不用等冷启动。
3. **持久层不需要为热切换做任何迁移**（详见 §3，有实测证据）。

顺带修正两处前置文档里的小偏差（本轮复核发现）：

- 前置文档说的「7 个 AV 判定点」**实为 6 个**：`SettingsRouteUtils.buildDomainOptions` 是**纯函数**、零 `SettingsRepository` 读取，只是静态枚举下拉项（`SettingsRouteUtils.kt:17-22`，且在 **commonMain** 而非 jvmMain）。
- `HomeSettingsRoute.kt:557` 也在 **commonMain**，不在 jvmMain。

---

## 1. 热切换要复现什么：进程内状态清单

| 状态类别 | 承载物 | 上游重启是否清 | 热切换处理 |
| --- | --- | --- | --- |
| 网络 service 的 `baseUrl` | `HanimeNetwork` 五个 service 实例 | ✅ 清（进程重建） | **显式 `rebuildNetwork()`**（§2.1） |
| App 级 ViewModel | `HomePageViewModel` + 它持有的 `mainBackStack` | ✅ 清 | **必须显式处理**（§2.2） |
| 路由级 ViewModel | `SearchViewModel` / `VideoViewModel` / … | ✅ 清 | **导航栈重置即自动销毁**（§2.3） |
| Compose `remember` 状态 | 各页面的 UI 暂存态 | ✅ 清 | `key(generation)` 重建 composition（§4） |
| 进程级 object 缓存 | `CsrfTokenProvider` / `TagLocalizer` / `PreviewCommentPrefetcher` | ✅ 清 | 逐个显式失效（§2.4） |
| 登录态 / Cookie | DataStore + `HCookieJar` + WebView Cookie | ✅ 清 | 现有 `logout()` 覆盖（§2.4） |
| **持久层（DB / DataStore / 磁盘缓存 / 下载）** | Room ×4 / `settings.preferences_pb` / `http_cache` | ❌ **不清** | **不动**（§3） |

一句话：**右列除最后一行外，全部是「进程内」的；把它们搞定，热切换就等价于上游重启。**

---

## 2. 五个必须处理的障碍

### 2.1 网络 service 的构造期 `baseUrl` 快照 —— 头号障碍

`baseUrl` 本身是**实时 getter**（`SettingsRepository.kt:60-66`），看起来动态，但它在 service **构造时被求值一次后冻死**：

```kotlin
// shared/src/commonMain/.../data/network/HanimeNetwork.kt:20-29
var hanimeService = _hanimeService        // ← 属性初始化器：HanimeNetwork 首次被访问时执行一次
    private set
...
private val _hanimeService get() = HanimeBaseService(createHanimeHttpClient())
```

```kotlin
// shared/src/commonMain/.../data/network/service/HanimeBaseService.kt:24
private val baseUrl: String = HANIME_BASE_URL      // ← 默认参数，构造瞬间求值，之后永久持有
```

同款「构造期快照」共 4 处：`HanimeBaseService.kt:24`、`HanimeCommentService.kt:22`、`HanimeMyListService.kt:34`、`HanimeSubscriptionService.kt:15`。
（Ktor 客户端本身不配 baseUrl —— `HttpClientFactory.kt:13-17` 的三个工厂都是无参的，所以「baseUrl」在本架构下**等价于 service 的构造参数**。）

**现成的重建入口有，但有两个问题**：

```kotlin
// HanimeNetwork.kt:46-52
fun rebuildNetwork() {
    rebuildHttpClients()
    hanimeService = _hanimeService
    getchuService = _getchuService
    commentService = _commentService
    myListService = _myListService
    // ← 漏了 subscriptionService（:43-44 定义、:28 持有）
}
```

- **缺陷 A**：漏重建 `subscriptionService` ⇒ 订阅页永远打旧站。
- **缺陷 B（更致命）**：切站路径**压根没调它**。同文件 `NetworkSettingsRoute.kt` 的 DoH / Hosts / 代理分支都有调（`:320`、`:338`、`:377`、`:487`），唯独**切网域那段（`:390-404`）没有**：

```kotlin
// shared/src/jvmMain/.../NetworkSettingsRoute.kt:390-404
onConfirm = {
    coroutineScope.launch {
        SettingsRepository.update { it.copy(domainName = ..., selectedBaseUrl = ...) }
        logout()
        restartApp(killProcess = true)      // ← 网络层没重建；靠"重启"掩盖
    }
}
```

⇒ 顺序必须是 **先 `update{}` 再 `rebuildNetwork()`**（否则新 service 仍读到旧 URL）。

### 2.2 App 级 ViewModel 活在 composition 之外

```kotlin
// shared/src/commonMain/.../app/App.kt:106-109
val homeViewModel: HomePageViewModel = sharedViewModel(::HomePageViewModel)
val backStack = homeViewModel.mainBackStack
```

`sharedViewModel`（`SharedViewModel.kt:21-28`）就是 androidx `viewModel()` ⇒ owner 是 `LocalViewModelStoreOwner.current`，即**平台级 ViewModelStore**（Android = `ComponentActivity`），**不属于 composition**。

两个后果：

1. `key(generation){}` 重建 composition **清不掉 `HomePageViewModel`** —— 它的 `_homePageFlow`（`HomePageViewModel.kt:42-43`）里还留着旧站的整页数据 ⇒ 会出现「**新站标题 + 旧站数据**」的混合态。
2. `mainBackStack` 是它的字段（`HomePageViewModel.kt:35`）⇒ **导航栈也在这儿**，不重置等于切换后还停在旧站的详情页上。

### 2.3 路由级 ViewModel 随 NavEntry 销毁（这条是好消息）

```kotlin
// shared/src/commonMain/.../app/navigation/main/SharedTopNavigation.kt:155-161
NavDisplay(
    backStack = backStack.backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),   // ← 每个 entry 自带 ViewModelStore
    ),
```

`rememberViewModelStoreNavEntryDecorator()` = androidx navigation3 的 per-entry store ⇒ **`SearchViewModel` / `VideoViewModel` 这类路由级 VM，随导航栈重置而销毁**（`onCleared` 正常触发）。

⇒ 只要把 `TopLevelBackStack` 重置回 `HomeRoute`，这一层自动干净，**不需要逐个 reset**。
（`SearchViewModel.kt:62-64` 的 `genres by unsafeLazy` 永久缓存因此也不再是问题 —— VM 没了，lazy 自然重来。它是「要清 VM」的理由，不是「要改 VM」的理由。）

### 2.4 进程级 object 缓存与凭据

| 对象 | 位置 | 是否需清 | 说明 |
| --- | --- | --- | --- |
| `CsrfTokenProvider.csrfToken` | `data/network/CsrfTokenProvider.kt:9` | **需要** | 全局单值、站点性凭据。被 5 个 VM import（`VideoViewModel:29`、`CommentViewModel:20`、`WatchLaterSubViewModel:10`、`OnlineWatchHistoryViewModel:13`、`MyPlayListViewModel:16`）；VM 会随栈销毁，但 object 不会 |
| `HCookieJar.cookieMap` | `jvmMain/.../HCookieJar.kt:25` | 已覆盖 | 按 host 分键；`logout()` 的 `clearMemoryCookies()` 已清空 |
| `TagLocalizer.cachedMappings` | `core/util/TagLocalizer.kt:24-36` | **需要** | 按语言缓存、**不以站点为键**；且 `:21` **只加载 `genre.json`，不加载 `genre_av.json`** ⇒ AV 站 genre 标签走番剧站映射表（**顺带发现的真 bug**，见 §5 P4） |
| `PreviewCommentPrefetcher.prefetcher` | `feature/video/PreviewCommentPrefetcher.kt:25-29` | 建议 | companion 静态单例，持有 `CommentViewModel`（跨站残留） |
| `CloudflareVerificationCoordinator.verifications` | `androidMain/.../CloudflareVerificationCoordinator.kt:59` | 不必 | 按 host 分键，天然隔离 |
| `HanimeDns` 的 DoH/Hosts 缓存 | `jvmMain/.../HanimeDns.kt:27,30` | 不必 | 与站点数据无关 |

登录态：`logout()`（`data/HanimeAccount.kt:22-32`）已覆盖 `isAlreadyLogin` / `loginCookie` / `savedUserId` + 内存 Cookie + WebView Cookie。
⚠️ 但 `loginCookie` 是**全局单值、按当前 host 无差别注入**（`HCookieJar.kt:33`）⇒ 切站**必须**清，否则把 A 站的登录 cookie 发给 B 站。CF cookie 反而是按 host 存的（`HCookieJar.kt:34-36`），可保留。

### 2.5 站点判定的 URL 归一化（**热切换的硬前提**）

6 个判定点统一用 `SettingsRepository.baseUrl == HanimeConstants.HANIME_URL[3]`。而 `baseUrl` 的真实形态是：

```kotlin
// SettingsRepository.kt:60-66
val baseUrl: String get() {
    if (current.useCustomMirrorSite && current.customMirrorSite.isNotBlank()) {
        val value = if (current.appendCustomMirrorPath) current.customMirrorSite
                    else rootUrl(current.customMirrorSite)
        return value.withTrailingSlash()          // ← 镜像地址，不是 javchu.com
    }
    return current.domainName
}
```

`HANIME_URL[3]` 是字面量 `"https://javchu.com/"`。于是判定会在这些情况下**静默失效**（退化成番剧站逻辑，不报错）：

| 失效条件 | 机理 |
| --- | --- |
| 开启自定义镜像 | `baseUrl` = 镜像 URL ≠ `javchu.com` |
| `domainName` 缺尾斜杠 | `"https://javchu.com"` ≠ `"https://javchu.com/"` |
| 大小写 / 协议差异 | 字符串直接比较 |
| `HANIME_URL` 增删元素或调序 | `[3]` 指向别处 |

现状下这就是个雷；热切换让 `baseUrl` 在**运行时可变**，踩中概率显著上升。**所以 P0 必须先把判定收口，再动切换机制。**

---

## 3. 持久层：为什么「不隔离」才是对的

热切换最大的诱惑是「顺手把两站数据隔离了」。**本轮不做**，理由三条，其中第一条是实测：

### 3.1 实测：两站 ID 数值区间重叠，但内容互不覆盖

| 取证 | 结果 |
| --- | --- |
| javchu 首页快照 `watch?v=` 提取 | 211 命中 / 144 去重，**全部 5–6 位纯数字**，样本 `403675 … 404516` |
| 本机真实 `history.db` 的 `WatchHistoryEntity.videoCode`（5 条） | `408116 / 408221 / 408225 / 408251 / 408266` —— **同为 6 位纯数字，区间相邻** |
| `javchu.com/watch?v=408116` | **HTTP 302**（不存在，跳首页） |
| `javchu.com/watch?v=408225` | **HTTP 302**（不存在） |
| `javchu.com/watch?v=404507` | **HTTP 200**（存在，1.05 MB） |

⇒ **两站是各自独立的 ID 空间，数值区间恰好重叠**。所以历史里那条 hanime1 的 `408116` 在 javchu 上**不会串成另一个视频**（它直接 302），最坏表现是「列表里混着另一站的条目，点进去打不开」。

### 3.2 上游也不隔离

上游 DataStore 与 Room 同样是单份、无站点字段。隔离会让我们**比上游更不一致**，且是 12 张表 + 迁移 + 全 UI 分区的工程。

### 3.3 结论

- **热切换不需要动持久层**：重启本来就不清它，复现「重启」自然也不需要清。
- 跨站列表污染是**真缺陷但独立于本次目标**，列 §8「已知残留」+ §5 P4（可选专项），不在 P0–P3 范围内。
- ⚠️ 唯一需要留意的边界：若将来发现**某 code 在两站都存在且内容不同**，才会升级成「点开看到完全不同的视频」。当前证据不支持该情况，但值得在 P5 回归时用几条交叉样本验证。

---

## 4. 方案设计

### 4.1 新增 `SiteSwitcher`（commonMain · `lovehan1me/site/`）

热切换的唯一收口。**顺序有讲究**，每一步都不能换位：

```kotlin
package lovehan1me.site

/**
 * 数据源热切换的唯一入口。
 *
 * 语义等价于上游的「改配置 + restart(killProcess=true)」，但不退进程：
 * 上游靠重启重建进程内一切；这里改为「显式重建 + 递增 generation 触发 UI 重建」。
 * 持久层（Room/DataStore/磁盘缓存）与上游一样**不动**。
 */
object SiteSwitcher {
    private val _generation = MutableStateFlow(0)

    /** UI 侧订阅它做 `key(generation){}` 全树重建；递增即等价一次"软重启"。 */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    suspend fun switchTo(
        target: String,                    // 目标 baseUrl（来自网域下拉）
        mirror: MirrorConfig,              // useCustomMirrorSite / customMirrorSite / appendCustomMirrorPath
    ) {
        val previous = SettingsRepository.baseUrl

        // 1) 先落配置 —— 必须在 rebuild 之前，否则新 service 仍读到旧 URL
        SettingsRepository.update {
            it.copy(
                domainName = target,
                // 对齐上游 MainActivity.kt:163-166：只在「从番剧站去 AV 站」时记住旧站
                selectedBaseUrl = if (previous in HanimeConstants.ANIME_URL) previous else it.selectedBaseUrl,
                useCustomMirrorSite = mirror.use,
                customMirrorSite = mirror.url,
                appendCustomMirrorPath = mirror.appendPath,
            )
        }

        // 2) 重建网络传输 + service 实例（含被遗漏的 subscriptionService）
        HanimeProxySelector.rebuildNetwork()
        HanimeNetwork.rebuildNetwork()

        // 3) 清站点性凭据
        logout()
        CsrfTokenProvider.csrfToken = null

        // 4) 清进程级缓存
        TagLocalizer.invalidate()
        PreviewCommentPrefetcher.reset()
        ParserLoggedNullKeysReset()        // 可选：Parser.kt:1207 的日志去重集合

        // 5) 通知 UI 全树重建（等价"重启"）
        _generation.value++
    }
}
```

### 4.2 三处必改的支撑件

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `HanimeNetwork.kt:46-52` | `rebuildNetwork()` 补 `subscriptionService = _subscriptionService` |
| 2 | `App.kt` | 订阅 `SiteSwitcher.generation`，用它 `key()` 包裹内容；并处理 App 级 VM（见 4.3） |
| 3 | `NetworkSettingsRoute.kt:390-404` | `onConfirm` 改为调 `SiteSwitcher.switchTo(...)`，删掉 `restartApp(killProcess = true)` |

### 4.3 App 级 VM 的两条路（需拍板，见 §6）

`HomePageViewModel` 的两个包袱是 **`_homePageFlow`（旧站整页数据）** 与 **`mainBackStack`（旧站导航位置）**。二选一：

**方案 A —— 换 ViewModelStoreOwner（彻底，推荐）**

```kotlin
@Composable
fun App(...) {
    val generation by SiteSwitcher.generation.collectAsStateWithLifecycle()

    // 每个 generation 一套全新的 store ⇒ 所有 viewModel() 都是新实例
    val store = remember(generation) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }   // 旧 store 的 VM 走 onCleared

    CompositionLocalProvider(LocalViewModelStoreOwner provides store) {
        key(generation) { AppContent(...) }
    }
}
```

- 优点：**不依赖逐个 VM 加 reset 方法**，新增 VM 自动被覆盖；`mainBackStack` 随 VM 一起换新，导航栈自动回 `HomeRoute`。
- 前置验证：Android 上 `MainActivity` 若有自己的 `by viewModels()` **会受影响**（需确认）；desktop/iOS 的默认 owner 是否可安全覆盖（CMP 已提供，覆盖应无碍）。

**方案 B —— 显式 `resetForSiteSwitch()`（保守，改动小）**

给 `HomePageViewModel` 加一个方法：重置 `mainBackStack`（`TopLevelBackStack` 需补一个 `reset()`）、把 `_homePageFlow` 置回 `PageState.Loading`、清 `_updateAnnouncement`；`App.kt` 在 generation 变化时调它并重新 `initializeHomePage()`。

- 优点：不碰 ViewModelStore 语义，风险可控。
- 缺点：**每新增一个 App 级 VM 都要记得加**，是长期的维护债。

> 我倾向 **A**：它让「新增 App 级 VM」这件事天然安全，而 B 的漏加是静默的（和 §2.5 那类静默失效同源）。但 A 需要先做一次 owner 覆盖的可行性验证。

### 4.4 视觉过渡

全树重建会有一瞬空白。建议在 `generation++` 之前先显示 `StartupGateBackdrop()`（`App.kt:59-69` 已有现成组件）或一个轻量遮罩，重建完成后首页骨架接管。**不要**让它看起来像崩溃——这次的闪退观感正是这么来的。

---

## 5. 分阶段实施

> 排序原则：**先把「判定」和「重建能力」修对，再动切换机制**。P0/P1 独立可验证，且不改变任何现有行为。

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| **P0** 判定收口 | `SiteConfig` 加 `isAvSite`；6 个判定点改走它；`baseUrl` 比较前归一化（尾斜杠 / 大小写 / 镜像） | 单测：`domainName` 带/不带尾斜杠、开镜像三种形态下 `isAvSite` 均为 true；6 处 grep 结果为零残留下标比较 |
| **P1** 重建能力 | ① `rebuildNetwork()` 补 `subscriptionService`；② `TopLevelBackStack` 加 `reset()`；③ `TagLocalizer.invalidate()` / `PreviewCommentPrefetcher.reset()` 等失效入口 | 单测：调 `rebuildNetwork()` 后 `subscriptionService` 的 `baseUrl` 等于新站 |
| **P2** 切站收口 | 新增 `SiteSwitcher`；`NetworkSettingsRoute.kt:390-404` 改调它（**此阶段仍保留 `restartApp`，用开关并行验证**） | 桌面端：切站后立即请求新站且不重启；日志出现新站 host |
| **P3** 软重启 | `App.kt` 接 `generation`；App 级 VM 按 §4.3 选定的方案落地；视觉过渡 | **视觉零变化前提下**，切站后首页/发现/我的全部显示新站数据，无混合态 |
| **P4** 入口与残留 | ①「切换站点」入口救活或删净（§7 决策点 2）；② `selectedBaseUrl` 语义已随 `SiteSwitcher` 归位；③ `AndroidManifest.xml:69-76` 深链补 `javchu.com`；④（可选）`TagLocalizer` 补 `genre_av.json` | 深链 `javchu.com/watch?v=` 能拉起 App；AV 站 genre 标签本地化正确 |
| **P5** 回归 | ① 用 `.workbuddy/_javchu_home.html` 加 javchu 首页快照解析测试（对标 `homePageVer2` 的 AV 分支 + 14 行映射）；② 三端冷/热切换冒烟；③ 交叉 code 抽样（§3.3） | `desktopTest` 全绿；Android 真机切站无闪退、无 `CrashActivity` |

**红线**：P0/P1 是纯内部重构，**不得改变任何既有 UI 行为**；P2 之前不许删 `restartApp` 路径（留作回退）。

---

## 6. 决策点（需要拍板）

1. **App 级 VM 处理**：§4.3 方案 A（换 Owner，彻底）还是方案 B（显式 reset，保守）？
   → 我建议 A，但需先花一次验证成本确认 `MainActivity` 没有独立依赖 `ViewModelStoreOwner`。
2. **「切换站点」入口**：救活（在账号页/设置页补回 AV ↔ 番剧开关，与网域下拉合并成一个入口）还是**删干净**（`onSwitchSiteClick` / `showSiteSwitchConfirm` / `AndroidOverlays` 的 ConfirmDialog / `switch_site`+`confirm_switch_site` 两套字符串 × 四 locale）？
   → 我建议**合并成一条**：热切换后两者机制已完全相同，留两条入口只会分叉语义。
3. **持久层跨站污染**：本轮保持与上游一致（不隔离，记入已知残留），还是排一个独立专项？
   → 我建议本轮不隔离（§3），把它作为独立议题单独立项。
4. **桌面端 `restartApp` 文案**：热切换后桌面不再退进程，设置页 `domain_change_tips` 那句「需要重新启动程式」要相应改写（两套字符串：`composeResources` + `app/res`）。

---

## 7. 风险与回退

| 风险 | 缓解 |
| --- | --- |
| 换 `ViewModelStoreOwner` 影响 Android 侧既有 ViewModel 语义 | P3 前先做单点验证；保留方案 B 作为降级 |
| 全树重建造成可见闪烁 | §4.4 遮罩；必要时退化为「只重建内容区 + 手动重置首页 VM」 |
| 漏清某个进程级缓存 ⇒ 跨站脏数据 | P5 用「同一 code 两站分别打开」交叉冒烟；`TagLocalizer` 这类非 host 分键的已逐个列出（§2.4） |
| 判定收口（P0）改错 ⇒ AV 站按番剧解析 | P0 有专门单测覆盖三种 URL 形态 |
| 热切换路径出现未预期崩溃 | **保留 `restartApp` 作为降级开关**（P2 阶段并行验证，P3 后再决定是否移除） |

回退策略：`SiteSwitcher.switchTo()` 内部最后一步是 `_generation.value++`；若 P3 出问题，只需把这一步换回 `restartApp(killProcess = true)`（Android `AppRestart` 另需修 `startActivity`+`exitProcess` 的抢跑问题，见前置文档 §4 嫌疑 1），其余改动均可保留。

---

## 8. 本轮不做

- ❌ 持久层按站点隔离（12 表 + 迁移 + UI 分区）—— §3.3
- ❌ `isAVSite` 升级为完整 `SiteId` 多站点抽象（javchu 在 `SiteCatalog.kt:13-24` 目前是 `Hanime1.baseUrls` 的第 4 项镜像），M6 再说 —— 本轮只加 `isAvSite` 这**一个**字段，不重构契约
- ❌ 动态新增第三个数据源 / 用户自定义站点
- ❌ 修改 `AppRestart` 三端语义（除非 P4 决定保留降级路径，那时再一并修 Android 的抢跑问题）

---

## 9. 实施记录（2026-09-17）

P0–P4 已落地，P5 单测已加；三端冷/热切换人工冒烟尚待跑。

### 9.1 与规划的偏离（都是实现时才发现必须改的）

| # | 规划写的 | 实际做法 | 为什么 |
| --- | --- | --- | --- |
| 1 | §4.3 方案 A「换 `ViewModelStoreOwner`」 | 换，但**提成进程级单点** `lovehan1me.app.AppViewModelStore`；`App()` 只调 `alignTo(generation)` 并把它喂给 `LocalViewModelStoreOwner` | store 若只换在 `App()` 的 CompositionLocal 里，`MainActivity` 仍握着旧一代的 `HomePageViewModel` —— intent 深链 / 返回键会压到一个已不在界面上的 `mainBackStack` 上，表现为「点了没反应」 |
| 2 | P1-② `TopLevelBackStack.reset()` | **未采用，已删除**（含 `startKey` 相关改动） | 换 store ⇒ 新 `HomePageViewModel` ⇒ `mainBackStack` 天然是新实例，`reset()` 一个调用点都没有。留着就是带假 KDoc 的死代码 |
| 3 | §4.4 视觉过渡（遮罩 / `StartupGateBackdrop`） | 未加 | 全树重建在桌面实测是一帧内完成，看不出空白；先不加，真出现闪烁再补 |
| 4 | P2「此阶段仍保留 `restartApp`，并行验证」 | 网域切换直接改调 `SiteSwitcher`，没有并行开关 | 热切换与重启是同一入口的二选一，并行反而要维护两套状态机。`restartApp()` 函数本身保留（内置 Hosts 变更仍在用） |
| 5 | §6 决策点 2「救活还是删干净」 | **救活**，入口按上游做法放在「我的」页账号卡（`MineScreen.AccountCard` → `SwitchSiteButton`）；抽屉残链（`onSwitchSiteClick` / `showSiteSwitchConfirm` / `AndroidOverlays` 里的对话框）**删净** | 两套字符串（`switch_site` / `confirm_switch_site`）本来就在，复用即可 |
| 6 | §6 决策点 3 持久层 | 不隔离（用户拍板，与上游一致） | 见 §3 |
| 7 | P0「`SiteConfig` 加 `isAvSite`」 | 新建 `lovehan1me.site.SiteIdentity`，判据用 `SettingsRepository.domainName`（新增）而非 `baseUrl` | 不改 `SiteConfig` 契约（§8 的红线）；`baseUrl` 在开镜像时返回镜像地址，做身份判定永远不成立 |

### 9.2 落地清单

**新增（commonMain）**
- `site/SiteIdentity.kt` —— 站点身份判定（`isAvSite` / `isAnimeSite` / `normalize` / `matches`）
- `site/SiteSwitcher.kt` —— 热切换唯一入口（`generation: StateFlow<Int>` / `switchTo` / `toggle` / `resolveToggleTarget`）
- `app/AppViewModelStore.kt` —— 当前代次的 `ViewModelStoreOwner` 单点 + `alignTo(generation)`（幂等 + 单调）

**改**
- `core/constant/NetworkConstants.kt`：`HanimeConstants.AV_URL` / `AV_HOSTNAME` 具名常量
- `data/SettingsRepository.kt`：`domainName` getter（站点身份的唯一可靠源）
- 5 个判定点改走 `SiteIdentity`：`Parser.homePageVer2`、`SharedHomeScreen`、`SearchViewModel.genres`、`VideoRouteHostScreen`、`HomeSettingsRoute.useAvHomeCategoryTitles`
- `data/network/HanimeNetwork.kt`：`rebuildNetwork()` 补 `subscriptionService`（P1-①，规划里的头号障碍）
- `core/util/TagLocalizer.kt`：`tagOptions` 改为可按站点重建的 `get()` + `invalidate()`；AV 站读 `genre_av.json`（此前硬编码 `genre.json`，是 AV 站标签本地化失效的真因）
- `feature/video/PreviewCommentPrefetcher.kt`：加 `reset()`
- `app/App.kt`：拆出 `AppContent()`；`AppToast.Host()` 移到 `key(generation)` 之外（否则正在显示的 toast 会随重建丢掉）；`key(generation)` 重建内容树
- `app/navigation/main/SharedTopNavigation.kt`：`MineTab` 内挂切站确认框
- `app/navigation/main/MineRoute.kt` + `feature/mine/MineScreen.kt`：账号卡加 `currentSiteName` 副标题 + 切站按钮
- `jvmMain/.../NetworkSettingsRoute.kt`：网域切换改调 `SiteSwitcher.switchTo(...)`
- `:app` `ui/activity/MainActivity.kt`：`viewModel` 改为从 `AppViewModelStore` 取（不再 `by viewModels`）；删死代码 `confirmSiteSwitch`
- `:app` `app/main/AndroidShell.kt`：清掉从未下传的切站参数链
- `app/src/main/AndroidManifest.xml`：`/watch` 深链补 `host="javchu.com"`

### 9.3 验证

- `./gradlew :shared:compileKotlinDesktop` ✅
- `./gradlew :shared:desktopTest --tests '*JavchuHomePage*' --tests '*SiteIdentity*'` ✅ 4/4 —— `SiteIdentityTest` 2 例 + `JavchuHomePageParseTest` 2 例（夹具 `.workbuddy/_javchu_home.html`，缺失则跳过）：钉住 AV 分支判定、14 行固定下标映射、以及 `newAnimeTrailer` 随 `isAVSite` 在**第 13 行 / 第 12 行**之间切换
- `./gradlew :app:compileDebugKotlin` ✅（内含 `:shared:compileAndroidMain`）
- `./gradlew :app:assembleDebug --max-workers=1` ✅ → `app/build/outputs/apk/debug/LoveHan1me-v26.3.2.apk`
  - ⚠️ 必须先加 `--max-workers=1`：不加会在 `transforms/.internal/locks/*.lock` 上随机撞 `FileNotFoundException(拒绝访问)`（炸点每次换任务），是 Windows 新建文件的共享竞争，不是代码问题。详见 `.workbuddy/memory/ENV-TOOLING.md`
- ❌ **未验证**：iOS 编译（本机无 Kotlin/Native 工具链，`han1me.ios.enabled=true` 但从未下载）；三端人工冒烟

### 9.4 已知残留 / 待办

1. ~~**决策点 4 未做**：`domain_change_tips` 仍写「需要重新启动程式」（两套字符串 × 各 locale）。~~ **已修**：改为「切换域名后会立即重新加载，无需重启程序！」，6 个文件（`app/src/main/res/values{,-zh-rCN,-zh-rTW}` + `shared/src/commonMain/composeResources/values{,-zh-rCN,-zh-rTW}`）同步；只动第一句，Cookie / 重定向 / 建议三段保留（`switchTo()` 确实会 `logout()`）。注意 `restart_or_not_working`（内置 Hosts 变更）仍在用 `restartApp`，未动。
2. **持久层不隔离**（§3）：历史 / 收藏 / 下载 / 打卡四个库跨站共用，列表里会混着另一站的条目；按 code 打开时 javchu 对 hanime1 的 code 返回 302，不会张冠李戴，但这是独立议题。
3. **上游继承来的空分支**：`Parser.homePageVer2` 里两处 `if (isAVSite) { extractHanimeInfo() } else { extractHanimeInfo() }` 两侧完全相同（`latestHanime` / `twoDAnime`）。核对上游 `Parser.kt:130-145` 确认**上游同款**，不是本轮引入的退化 —— 本轮不动，避免顺手改解析行为。
4. **夹具内容特征**：抓到的 javchu 首页第 6 行（3DCG）留着未水合的占位 href（`watch?v=6` / `v=5`），且第 1/5/10 行是同一支片子的重复推荐。这是站点内容特征，测试里刻意不断言「videoCode 互不重复」。

---

## 附：关联文件索引

**必改**
- `shared/src/commonMain/.../data/network/HanimeNetwork.kt`（`:46-52` 补 subscriptionService）
- `shared/src/commonMain/.../app/App.kt`（`:106-109` App 级 VM / `:155` NavDisplay 宿主）
- `shared/src/jvmMain/.../app/navigation/settings/NetworkSettingsRoute.kt`（`:390-404` 切站收口）
- `shared/src/commonMain/.../app/navigation/main/TopLevelBackStack.kt`（补 `reset()`）

**新增**
- `shared/src/commonMain/.../site/SiteSwitcher.kt`
- `shared/src/desktopTest/.../site/JavchuHomeSnapshotParsingTest.kt`（fixture：`.workbuddy/_javchu_home.html`）

**判定点（P0 全改）**
- `site/hanime1/Parser.kt:79`（→ `:144,148,159,171`）
- `feature/home/homepage/SharedHomeScreen.kt:79`（→ `:183`）
- `feature/search/SearchViewModel.kt:63`
- `feature/video/VideoRouteHostScreen.kt:178`（`:174` 是 `LaunchedEffect` 的 key —— 已按 baseUrl 做 key，是全仓库唯一的好范式，保留）
- `app/navigation/settings/HomeSettingsRoute.kt:557`
- `core/constant/NetworkConstants.kt:26-28`（`HANIME_HOSTNAME` / `HANIME_URL` / `ANIME_URL`）

**关键参考**
- `shared/src/commonMain/.../data/SettingsRepository.kt:60-66`（`baseUrl` getter）
- `shared/src/commonMain/.../data/HanimeAccount.kt:22-32`（`logout()` 清理范围）
- `shared/src/commonMain/.../data/network/CsrfTokenProvider.kt:9`
- `shared/src/commonMain/.../core/util/TagLocalizer.kt:16-36`（`:21` 只读 `genre.json`）
