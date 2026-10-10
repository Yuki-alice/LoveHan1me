# LoveHan1me 网络架构分析

> **分析对象**：`E:\LoveHan1me`（Kotlin Multiplatform：`:shared` + `:app` + `:desktopApp` + `iosApp` + `:video:×4` + `echgate`）
> **分析日期**：2026-10-10
> **分析方式**：全量静态阅读 —— `shared/src/{commonMain,jvmMain,androidMain,iosMain,desktopMain}/**/data/network/**`（88 个 `.kt`）、`infra` 侧 `echgate/`（Go）、`app/src/main/kotlin/lovehan1me/echgate/`
> **规模**：网络层主源 **63 文件 / 6,399 行**，测试 **25 文件 / 4,219 行**
> **文档定位**：本项目网络层的现状基线；与 `Han1meViewer网络架构分析.md`（参考项目）配套阅读，§12 给出逐维对照
> **本机文件**：`docs/` 已被 gitignore，本文档不入库

---

## 一、总体结构

### 1.1 分层图

```text
        消费方：UI/ViewModel · 播放器（:video:engine）· 下载 · WebView（CF 验证）
                                  │
                                  ▼
        NetworkRepo ── flow 三件套 · CF 验证后自动续跑 · 异常映射
                                  │
                                  ▼
        HanimeNetwork ── 5 个 Ktor service（Retrofit 已删除，站点地址每请求实时解析）
                                  │
                                  ▼
        HttpClient 工厂（expect/actual）
          JVM: Ktor(OkHttp) preconfigured = ServiceCreator.{h,download,getchu}Client
          iOS: Ktor(Darwin) + EchGateClientPlugin
                                  │
                                  ▼
        ┌───────── EgressScheduler（纯函数判定，唯一的出口决策产出处）─────────┐
        │   DomainClass × RouteHealth × ForceMode × EgressBudgets → ScheduledPlan │
        └───────────────────────────┬───────────────────────────────────────────┘
                                    │
                 ┌──────────────────┴──────────────────┐
                 ▼                                     ▼
          RouteId.Gate                          RouteId.Default
        （本地 ECH 网关）                    （客户端当前出口）
                 │                                     │
                 ▼                                     ▼
        Go echgate 127.0.0.1                  HanimeProxySelector
        三策略：ECH / plain / alias            Direct / System / HTTP / SOCKS
                 └──────────────────┬──────────────────┘
                                    ▼
                    HanimeDns（四级降级 + DoH 负缓存 + 连通性探测）
                                    ▼
                              系统网络
```

### 1.2 与参考项目的根本分歧

参考项目 `Han1meViewer` 改造的是**进程内的 TLS 栈**（Conscrypt 反射契约注入 ECHConfigList + Rust quiche 自建 QUIC）。
本项目改造的是**出站决策**：ECH 交给一个独立的 Go 网关进程，App 侧只负责「把请求改写进去」和「决定要不要改写」。

| 维度 | 参考 `Han1meViewer` | 本项目 `LoveHan1me` |
|---|---|---|
| ECH 载体 | 进程内 Conscrypt（TLS）+ Rust quiche（QUIC H3） | **进程外 Go 网关**，监听 `127.0.0.1:<port>`（iOS/Android 为进程内起服，共用同一份 `gate.Start`） |
| App 侧职责 | 注入 ECHConfigList、自建 H3 传输、WebView 三层接管 | **URL 改写 + 出口决策**（`EchGatePolicy` 59 行是枢纽） |
| 出口选择 | 无（ECH 是全局开关） | **按域动态排表**：熔断 + EWMA 择优 + 粘滞锁定 |
| 失败语义 | fail-closed / 明文降级（写在策略里） | **按域记账** + 熔断 + 主动让位给代理（同码免罪） |
| 平台覆盖 | 仅 Android | **三端共用决策层**（Android / Desktop / iOS），执行器各一 |
| 可测性 | 依赖真实网络 | 判定全是**纯函数**，`commonTest` 离线断言 |
| 配置热切换 | `rebuildNetwork()` 重建 client | client 是**稳定单例**，设置每请求实时读 |
| H3 | Rust quiche 静态链入 `libchino.so` | 网关侧走 CNAME 真名降级（`alias`），不做 QUIC |

### 1.3 三条架构主张

这套设计可以归纳为三条被反复强调、并在注释里给出反例的主张：

| 主张 | 落地方式 | 反例（注释里记录的） |
|---|---|---|
| **判定与执行分离** | `EgressScheduler` 排表（纯函数）→ 两个执行器消费同一份 `ScheduledPlan` | 「判定分叉正是"页面能开、视频打不开"类问题的根因」 |
| **网关是加速项，不是依赖** | `port <= 0` 时所有改写层零开销放行；网关失败连累不到正常请求 | 「网关挂了不该连累正常请求」 |
| **状态必须可区分** | `EchGateStatus` 六态密封接口；`NoRouteException` 带域与原因；`EgressEvent` 带 rtt / budget | 「"用户主动关"与"进程意外死亡"在类型上无法区分，设置页只能把两者都报成失败」 |

### 1.4 技术栈

| 层 | 技术 |
|---|---|
| HTTP | Ktor Client 3（`OkHttp` 引擎 / `Darwin` 引擎）+ OkHttp 拦截器链 |
| DNS | OkHttp `Dns` 接口 + `okhttp-dnsoverhttps` |
| ECH | Go `crypto/tls`（`EncryptedClientHelloConfigList`）+ gomobile 绑定 |
| 序列化 | 站点接口返回 `HttpResponse`，HTML 交 Jsoup 系解析器 |
| 图片 | Coil 3（经 `ImagePipeline` + `CdnFetchClient`） |
| 后台 | WorkManager（Android 下载） |

---

## 二、Client 层：ServiceCreator 与三端工厂

**文件**：`jvmMain/.../data/network/ServiceCreator.kt`（160 行）、`commonMain/.../HttpClientFactory.kt`（21 行）

### 2.1 三个稳定单例

与参考项目「改设置要重建 client」方向相反，本项目的 client 是 `val`：

```kotlin
/**
 * 三个客户端只依赖不随设置变化的参数（超时 / 协议 / 缓存目录）。出口判定
 * （DNS、代理、网关）、UA、Cookie 全部在拦截器里每请求读取实时设置，
 * 所以它们是**稳定单例**：改任何网络设置都不需要重建。
 */
val hClient: OkHttpClient = buildHClient()
val downloadClient: OkHttpClient = buildDownloadClient()
val getchuClient: OkHttpClient = buildGetchuClient()
```

| Client | 超时 | 关键配置 |
|---|---|---|
| `hClient` | connect 15s / read 30s / call 60s | `retry` → `UserAgent` → `UrlLogging` → `gate(Api)` → `Cache(10MB)` / `HCookieJar` / `HanimeProxySelector.SHARED` / `HanimeDns.SHARED` → `CloudflareInterceptor?` |
| `downloadClient` | connect 5s；**刻意无 read/call** | `protocols(HTTP_1_1)` → `retry` → `UserAgent` → `gate(Download)` → 代理 / DNS |
| `getchuClient` | connect 15s / read 30s / call 60s | `retry` → `UrlLogging` → `GetchuInterceptor` → `gate(Api)` → `NO_COOKIES` / 代理 / DNS |

两处刻意的设计：

**① `retryInterceptor` 挂最外层：**

> *放在每条链的**最外层**：重试要重跑整条链（含网关改写与 UA 覆盖），而不是只重放最内层的网络调用。*

**② 网关改写挂在日志之后：**

> *放在日志之后：日志记录的是改写前的真实 URL，排查时才有意义。*

**③ 下载链不配 read/call 超时**（与 `EgressPurpose.Download = UNLIMITED` 同语义）：

> *长连接传文件不能掐（慢流会被误杀）……连接阶段由 connect 5s + Retry 总预算兜底。*

### 2.2 换网摘池：登记制

此前是写死清单，漏过一整轮（桌面下载控制器那组独立池）：

```kotlin
private val extraPoolEvictors = CopyOnWriteArrayList<() -> Unit>()
internal fun registerConnectionPoolEvictor(evict: () -> Unit) { extraPoolEvictors += evict }

fun evictConnectionPools() {
    hClient / getchuClient / downloadClient.connectionPool.evictAll()
    extraPoolEvictors.forEach { runCatching { it() } }   // 单个登记项抛异常不连累其余
    evictCdnConnectionPools()
}
```

> *改成登记制后：**谁建池谁登记**，复位入口不必认识任何一个具体客户端。*

`evictAll()` 只摘空闲连接（`allocationCount == 0`），在途请求与下载不受影响——这条假设被 `NetworkChangeReactionsTest` 固定住。

### 2.3 Ktor 工厂（expect / actual）

```kotlin
// commonMain
expect fun createHanimeHttpClient(): HttpClient
expect fun createGetchuHttpClient(): HttpClient
expect fun createPlainHttpClient(): HttpClient   // 第三方 API（弹弹play），不带任何站点 cookie / 拦截器 / 代理
```

```kotlin
// androidMain / desktopMain（两文件逐字节相同）
actual fun createHanimeHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = ServiceCreator.hClient }   // ← 复用整条 OkHttp 拦截器链
}
```

> *android / desktop：Ktor(OkHttp) + preconfigured 复用 jvmMain ServiceCreator 的整条拦截器链（HanimeDns / HCookieJar / HanimeProxySelector / UserAgent / UrlLogging / Cloudflare / cache），不再 install HttpCookies，timeout 语义跟随 OkHttp。*

iOS 走 Darwin 引擎（`HttpClientFactory.ios.kt` 56 行），装配顺序有硬约束：

```kotlin
private fun createDarwinHttpClient(purpose: EgressPurpose): HttpClient = HttpClient(Darwin) {
    // ECH 网关插件必须装在 HttpCookies 之前：改写先发生，storage 随后对回环短路。
    installEchGate(defaultPurpose = purpose)
    install(HttpCookies) { storage = BridgeCookiesStorage() }
    install(HttpTimeout) { /* Api: request 60s / connect 15s / socket 30s —— 对齐 JVM */ }
    install(HttpRequestRetry) { noRetry(); retryOnExceptionIf(maxRetries = 2) { ... }; constantDelay(300, 1_000, true) }
}
```

> *状态码一律不重试：网关的 502 重试与 CF 验证后的续跑各自负责那一层，在这里再叠一层会让两套机制互相看不见。*

### 2.4 HanimeNetwork（30 行）

```kotlin
object HanimeNetwork {
    private val hanimeHttpClient = createHanimeHttpClient()
    private val getchuHttpClient  = createGetchuHttpClient()
    val hanimeService       = HanimeBaseService(hanimeHttpClient)
    val getchuService       = GetchuService(getchuHttpClient)
    val commentService      = HanimeCommentService(hanimeHttpClient)
    val myListService       = HanimeMyListService(hanimeHttpClient)
    val subscriptionService = HanimeSubscriptionService(hanimeHttpClient)
}
```

> *站点地址由各 service 每次请求从 `HANIME_BASE_URL`（其本身是读设置的 getter）实时解析……所以无论是切换站点还是改任何网络设置，都不需要重建实例。*

**这一点修掉了参考项目的一个实际缺陷**（参考项目 `rebuildNetwork()` 漏重建 `subscriptionService`）。

### 2.5 Service 层（commonMain，5 个）

| Service | 行数 | 说明 |
|---|---|---|
| `HanimeBaseService` | 113 | 首页 / 搜索 / watch / previews / login / subscriptions + **G2-1b-2 作者页三条**（`getArtistPage` / `getArtistUploaded` / `getAuthorPlaylists`）+ `getSitePlaylist` |
| `HanimeMyListService` | 291 | 收藏 / 播放列表 / 在线历史 / 账号（含头像上传） |
| `HanimeCommentService` | 132 | 评论 / 回复 / 点赞 / 举报 |
| `HanimeSubscriptionService` | 38 | 订阅作者 |
| `GetchuService` | 103 | Getchu 预览 / 详情 / 系列 AJAX |

全部返回 `HttpResponse`（`Response<ResponseBody>` → `HttpResponse` 的 Ktor 化适配收在 `NetworkRepo` 一处）。

---

## 三、出口调度器（`data/network/egress/`）

这是本项目相对参考项目**最大的增量**，12 个文件、约 1,000 行，全部可进 `commonTest`。

### 3.1 设计主张：判定必须是纯函数

```kotlin
fun plan(
    request: EgressRequest,
    state: EgressState,
    health: RouteHealth = RouteRegistry.healthOf(classifyDomain(request.url, request.purpose)),
    nowMs: Long = currentEpochMillis(),
): ScheduledPlan
```

> *本对象只读快照、不碰任何全局：健康由调用方传入（执行器传 `RouteRegistry.healthOf`，测试传手造状态），因此"受限域无直连""强制覆盖""预算推导"全都能离线断言。*

### 3.2 排表规则（Auto 模式）

```text
1. 网关可用（开着、有端口、改写成立、该域未熔断）→ 首位；第三方域永不进网关
2. 其余一律折叠为一条 RouteId.Default
   进入条件（三者取或）：有可用代理 / 非受限域（第三方）/ 用户关掉了网关
   网关开着但没跑起来时，受限域也不排 Default（已知撞 RST，不浪费时间）
3. 粘滞优选（未熔断）置顶，其余按 成功率降序 → EWMA RTT 升序
4. 表空 = 无可用出口：抛 NoRouteException，不转圈
```

关于第 2 条里「用户关掉了网关」这个条件，注释给了解释：

> *回到旧语义 —— 关开关等于声明"我的直连可用"，加速项缺席不该连累正常请求；海外用户活在这里。*

### 3.3 路由折叠（`RouteId`）

```kotlin
enum class RouteId {
    Gate,        // 本地 ECH 网关改写道（唯一真能按请求切换的出口）
    Default,     // 客户端当前出口：代理还是直连由设置决定，HTTP 执行层不区分
    GateTunnel,  // 网关 CONNECT 隧道（播放器 / CF 验证窗用，非 HTTP 执行器的路由）
}
```

折叠掉 `UserProxy` / `SystemProxy` / `Direct` 是一条**证据驱动**的决策：

> *那三者在两个执行器里**物理等价**……把它们排成三条只会把**同一个物理出口**的成败记到三个假名字上（审阅 F4）——"设置是代理时，Direct 步实际走代理，成功却记到 Direct 头上"。*

配套的诚实命名（`EgressStatus.kt`）：

```kotlin
fun RouteId.displayName(): String = when (this) {
    RouteId.Gate -> "网关"
    RouteId.Default -> "当前网络出口"   // 不谎称"直连"
    RouteId.GateTunnel -> "网关隧道"
}
```

播放层仍需细分（SOCKS 解析不出时退隧道），那份区分改由 `EgressState.proxy` 推导，不再依赖路由名。

### 3.4 域分类（`DomainClass`）

```kotlin
enum class DomainClass { Hanime, Getchu, CdnMedia, ThirdParty }
val DomainClass.isRestricted: Boolean get() = this != DomainClass.ThirdParty

fun classifyDomain(rawUrl: String, purpose: EgressPurpose): DomainClass {
    val host = runCatching { Url(rawUrl).host.lowercase() }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return DomainClass.ThirdParty
    if (HanimeConstants.HANIME_HOSTNAME.any { it.equals(host, ignoreCase = true) }) return DomainClass.Hanime
    if (host == GETCHU_HOST) return DomainClass.Getchu
    return when (purpose) {
        Image, Video, Download -> DomainClass.CdnMedia   // 图床 / CDN 直连同样不可信
        Api, Probe -> DomainClass.ThirdParty
    }
}
```

> *解析失败一律 ThirdParty：分类器永远不能成为请求失败的原因。*

### 3.5 健康度（`RouteHealth`，116 行）

常量集中一处：

```kotlin
const val FAILURE_THRESHOLD = 3        // 非阻断类失败累计到此数才熔断
const val COOLDOWN_MS = 5 * 60 * 1000L // 熔断冷却；到期后半开
const val LOCK_SUCCESS_THRESHOLD = 3   // 连续成功到此数锁定优选
const val WINDOW_SIZE = 10             // 成功率窗口（位图 10 位）
```

三类结局的处理：

| `AttemptOutcome` | 熔断行为 |
|---|---|
| `Success` | 清零失败计数、推窗口、更新 EWMA、解除熔断 |
| `Blocked`（网关出口被封） | **一次即熔** |
| `TransportError` / `GatewayErrorPage` | 攒够 `FAILURE_THRESHOLD` 才熔 |

EWMA RTT（`超时/取消不记 RTT`，`rttMs < 0` = 无样本）：

```kotlin
ewmaRttMs = when {
    sample == null -> current.ewmaRttMs
    current.ewmaRttMs < 0 -> sample
    else -> (current.ewmaRttMs * 7 + sample) / 10
}
```

**粘滞优选**（防 flap）：

```kotlin
// 只有"无锁定 + 刚达阈值"才加锁；锁定路由失败即解锁；别家成功不抢锁（防 flap）。
val nextLocked = when {
    route == lockedRoute && outcome != AttemptOutcome.Success -> null
    lockedRoute == null && outcome == AttemptOutcome.Success &&
        next.consecutiveSuccesses >= LOCK_SUCCESS_THRESHOLD -> route
    else -> lockedRoute
}
```

### 3.6 预算（`EgressBudgets`）

```kotlin
object EgressBudgets {
    const val UNLIMITED: Long = Long.MAX_VALUE   // 流式不掐：播放/下载的 read 无上界，靠 stall 检测

    fun budgetFor(purpose: EgressPurpose): Long = when (purpose) {
        Api -> 60_000L              // 沿用 hClient 的 call 级 60s
        Image -> 30_000L            // 读停滞 30s 还没完就是死了
        Video, Download -> UNLIMITED
        Probe -> 10_000L            // 单 IP 量级
    }
}
```

有一处**主动删掉的设计**：签名原带 `route` 参数但从未被读过。

> *⚠️ **只有 purpose 一维**。原签名带 `route` 参数，但它从未被读过——预算表在文档里被写成"purpose × route 二维"，实现却是一维，多出来的那个参数会让读代码的人以为"不同路由有不同预算"。直到真的有路由维度的预算，不要把它加回来。*

**预算的执行边界也诚实标注了**（`EchGateInterceptor` KDoc）：

> *[budgetMs] 在 attempt 边界与重试门控处执行（502 重试前自查超支即停）；socket 级硬上限仍是各 client 的 connect/read/call 超时。在途 IO 不能被抢占 —— 这是 OkHttp 同步链的固有限制，不撒谎。*

### 3.7 强制模式（`ForceMode`）

```kotlin
enum class ForceMode { Auto, ForceGate, ForceDirect, ForceProxy
    companion object { fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: Auto }
}
```

> *[Auto] 之外全部短路健康度：ForceGate 无视熔断，ForceDirect 在受限域上预期撞 RST（设置页弹警告），ForceProxy 在无代理时直接诚实失败。*
>
> *未知值读回 Auto：枚举增删不炸老数据与脏备份。*

### 3.8 记账与诊断

**`RouteRegistry`**（44 行）——各域健康的唯一持有者，`update` 必须加锁：

```kotlin
private val lock = PlatformLock()
@Volatile private var snapshot: Map<DomainClass, RouteHealth> = emptyMap()

fun update(domain: DomainClass, transform: (RouteHealth) -> RouteHealth) {
    lock.withLock { snapshot = snapshot + (domain to transform(healthOf(domain))) }
}
fun reset(domain: DomainClass? = null)   // null = 全清（换网）；传域 = 只清该域（切站/单域自愈）
```

加锁的理由是量化的：

> *原先它是"读快照 → 算新值 → 写回"三步，靠 `@Volatile` 保证可见性但不保证原子性：图片链几十个请求并发上报时，两次更新会互相覆盖，**熔断计数少算一次**。成功率排序少一次无所谓，但熔断计数少一次意味着本该在第 3 次熔断的路由要拖到下一次，而 `COOLDOWN_MS` 是 5 分钟 —— 代价不是"少一条上报"，是"多 5 分钟坏路由"。*

**`EgressReporter` + `EgressEvents`**（74 行）——一次上报做两件事：

```kotlin
fun report(domain, route, outcome, rttMs, nowMs, budgetMs) {
    val before = RouteRegistry.healthOf(domain)
    RouteRegistry.update(domain) { before.onResult(route, outcome, rttMs, nowMs) }
    EgressEvents.emit(EgressEvent(nowMs, domain, route, outcome, rttMs, budgetMs))   // ring buffer 近 200 条
    if (route == RouteId.Gate) { /* 熔断/恢复跳变打一行日志 —— 自动化验收与人工排障都靠它断言 */ }
}
```

**`EgressStatus`**（184 行）——设置页「连接三态」的唯一计算处，纯函数、**只产结构不产文案**：

```kotlin
data class EgressStatusSnapshot(
    val gate: EchGateStatus,
    val meltedDomains: List<DomainClass>,      // 熔断中的域
    val unstableDomains: List<DomainClass>,    // 有过失败但还没熔断
    val maxRecentFailures: Int,
    val noRoute: Boolean,                      // 网关开着、没跑起来、也没代理
) { val isClean get() = meltedDomains.isEmpty() && unstableDomains.isEmpty() && !noRoute }
```

抽出来的两个动机：

> *1. **iOS 用不了** —— 它不在 commonMain，iOS 网络页只能退化成占位符，用户看不到"网关是不是被熔断了"，而调度器其实一直在跑；*
> *2. **测不了** —— 内联在 `@Composable` 里的一坨 `when` 没有任何单测。*
>
> *本文件**只产出结构，不产文案**。域名的展示名、条目的措辞由各端用自己的 `strings.xml` 拼 —— 纯函数里塞 `stringResource` 是它当初逃不出 `@Composable` 的原因。*

`noRoute` 的判据与调度器同口径：

```kotlin
noRoute = useEchGate && EchGate.port <= 0 && !proxyUsable
// 用户主动关掉网关是声明"我的直连可用"，那时不该贴"无可用出口"。
```

### 3.9 诚实失败（`NoRouteException`，17 行）

```kotlin
class NoRouteException(val domain: DomainClass, val reason: String)
    : okio.IOException("无可用出口（$domain）：$reason")
```

继承 `okio.IOException` 不是随手写的，注释记录了 2026-10-04 的桌面崩溃：

> *必须继承 `okio.IOException` 而不是普通 `Exception`：OkHttp 只把 `IOException` 递送给 `Callback.onFailure`（Coil 图片走 `enqueue`），非 IO 异常会从 `RealCall$AsyncCall` 的裸分发线程逃逸成未捕获异常 → 崩溃退出（2026-10-04 桌面端实测：取消风暴误熔断 CdnMedia 后首个图片请求即 exit 10）。*

---

## 四、执行器

两个执行器消费**同一份** `ScheduledPlan`、用**同一套** `EgressReporter` 记账，只把"怎么发请求"换成各自引擎的写法。

### 4.1 OkHttp 执行器（`EchGateInterceptor`，347 行）

```kotlin
class EchGateInterceptor(
    private val attachSiteCookies: Boolean = true,          // 图片链必须传 false
    private val defaultPurpose: EgressPurpose = EgressPurpose.Api,
) : Interceptor
```

**`attachSiteCookies = false` 的理由**：

> *图片/封面链必须传 false：`loadForRequest` 会把 `hanime1_session` 一类的登录态一并取出，而那些图床是**第三方**——把会话凭据发过去没有任何用途，只有泄漏风险。*

**从旧实现原样保留的三个洞**（改写后必然出现的问题）：

| # | 洞 | 解法 |
|---|---|---|
| 1 | 改写后按 `127.0.0.1` 匹配域名，登录态与 `cf_clearance` 拿不到 | 按**原域名**取出后塞进 `Cookie` 头 |
| 2 | `Set-Cookie` 按 `127.0.0.1` 存下，原域名再也取不到 | 用原 URL 重新解析存回去（`finishGateResponse`） |
| 3 | 网关出口被封返回 403 而不是异常 | **有后路**才挂起比对；**没后路原样交出去**（多半是 CF 挑战，`NetworkRepo` 靠它的 body 触发验证窗） |

**取消不喂熔断器**（2026-10-04 桌面崩溃的直接教训）：

```kotlin
} catch (e: IOException) {
    // 调用方主动取消（Coil 滑走、关窗 teardown）不是路的问题：
    // 不喂熔断器，直接抛。否则取消风暴会把好端端的网关熔断，
    // 下一个请求诚实失败 —— 2026-10-04 桌面崩溃的完整链条。
    if (isCallCanceled(chain)) throw e
    ...
}
```

**让位定责**（`gateBlameAfterYield`，纯函数）：

```kotlin
/**
 * - 让位路径与网关**同一个码** ⇒ 封的是用户/站点，不是网关的出口 ⇒ **网关无责，不记账**
 *   （否则用户会因为"站点就是不让看"而被熔断掉一条本来能用的路）；
 * - 让位路径拿到了不同的结果 ⇒ 网关的出口被封 ⇒ 记一次**阻断类**失败，一次即熔断。
 */
fun gateBlameAfterYield(yieldCode: Int, gateCode: Int): Boolean = yieldCode != gateCode
```

**非幂等方法的完整约束**（三处一致）：不能重试、不能让位、失败即抛；网关 502 时把网关那份**原样交出**但记一次失败（否则「POST 一直撞 502」永远攒不到熔断）。

### 4.2 Ktor 执行器（`EchGateClientPlugin`，305 行）

```kotlin
val EchGateClientPlugin = createClientPlugin("EchGate", ::EchGatePluginConfig) {
    on(Send) { request -> /* 同一套 plan → 同一套 GateStep → 同一套 report */ }
}
```

### 4.3 两个执行器的**刻意**差异

| # | 差异 | 原因 |
|---|---|---|
| 1 | **502 不验错误页前缀** | `bodyAsText()` 会把响应消费掉，返回给调用方的就是空壳；网关的 502 恒由 `onUpstreamError` 产生，按状态码判定已足够准 |
| 2 | **代理/直连步完全交给 NSURLSession** | 恢复原 URL 后直接发，系统代理自动生效；用户手填的显式代理**不会**被应用（Darwin 引擎无按请求配代理的口子）—— **已知缺口** |
| 3 | **无就绪等待** | iOS 起服由 Swift 壳管、`EchGatePortReporter` 被动回填，Kotlin 侧没有"拉起中"状态可等 |

### 4.4 执行流程与让位定责

```text
① plan()  纯函数排表
      │
      ▼
② 逐条执行 attempts（粘滞优选置顶，其余按成功率降序）
      │
      ├─ Gateway 步 ──► 改写 URL + X-Ech-Target + 按原域名补 Cookie
      │                    ├─ 502 上游错误页 → 幂等则重试一次（预算门控）/ 非幂等原样交出
      │                    ├─ 403 出口被封   → 有后路则挂起比对定责 / 无后路原样交出
      │                    └─ IO 异常       → 记传输失败，让位下一步
      │
      ├─ Default 步 ──► 恢复原 URL 直接发（出口由代理选择器决定）
      │                    ├─ 取消 → 直接抛，不喂熔断器
      │                    ├─ 失败 → 记失败；非幂等即止；幂等末步按计划形态分流
      │                    └─ 成功 → 若网关指控挂起，比对：同码免罪 / 异码熔断
      │
      ▼
③ 全败 → NoRouteException（诚实失败，不转圈）
```

---

## 五、本地 ECH 网关（`echgate`，Go）

**文件**：`echgate/main.go`（95 行，CLI）、`echgate/gate/gate.go`（846 行，核心）、`echgate/gate/mobile.go`（29 行，gomobile facade）

### 5.1 它解决什么

> *直连被 SNI 阻断的站点时，TLS 的 ClientHello 里 SNI 是明文，DPI 看到就 RST。实测（2026-09-22）：TCP 能握手（0.2–1.0s），TLS 阶段必被重置；加 `-k` 忽略证书校验也一样 ⇒ 不是证书问题。*

### 5.2 两条通道（同一端口，按请求方法分流）

| 通道 | 协议形态 | 能力 |
|---|---|---|
| **反向代理**（主力） | `GET http://127.0.0.1:<port>/path` + `X-Ech-Target: hanime1.me` | 网关**代为** TLS 握手 → **能用 ECH**、能换 SNI 成 CNAME 真名 |
| **CONNECT 隧道**（兜底） | `CONNECT host:443` | 客户端在隧道内自己做 TLS，SNI 对网关不可控、仍是明文 → 只剩 **DoH 解析干净 IP + 逐 IP 拨号** |

关于隧道为什么只作第二选择，注释里有一段**明确的反向警告**：

> *⚠️ 上游 Han1meViewer 的 `echproxy.handleConnect` 也是纯隧道（它注释写着 "ECH is negotiated by the client's own TLS inside the tunnel"）。所以照搬上游只会把主力通道的 ECH 能力换掉，是对 CF 站点的**降级**，不是升级。*

### 5.3 三种出站策略（`plan`）

```go
type plan struct {
    ips      []string  // 按握手延迟升序的候选 IP；拨号按序尝试，单个失败换下一个
    dialHost string    // SNI 与 Host 用哪个名字（CNAME 降级时会换成真名）
    useECH   bool
}
```

`probePlan` 三档探测：

| 档 | 条件 | 做法 | 依据 |
|---|---|---|---|
| ① **ECH** | `isCFHost(host) && len(currentECH()) > 0` | 候选 = 该域 DoH IP **+** 调用方给的 IP 池（去重），**必须用 ECH 握手探测** | 见下两条 |
| ② **alias** | 有 CNAME 且 ≠ 原名 | 用真名做 SNI+Host，对真名 IP 探测 | CDN77 实测 |
| ③ **plain** | 兜底 | 用该域自己的解析结果普通 TLS | — |

第 ① 档的两条反直觉约束：

> *只对调用方点名的站点（cf-hosts）做这一步，不能对任意域名都试：ECH 握手到 CF 边缘时，外层 SNI 是 `cloudflare-ech.com`，CF 会照常接受握手，于是"非 CF 站点"也会被误判为可用——实测视频 CDN 就这样被错判，结果走了一条慢得多的路径（40 秒都没下完）。*
>
> *⚠️ 探测必须用 ECH 握手：这类域名的普通 TLS 会被重置，拿普通握手去试会把 CF 站点误判成"不是边缘"。*

第 ② 档是本项目相对参考项目的**净新增能力**（参考项目只能在响应 body 层做字符串替换）：

> *第三条是实测挖出来的：视频直链 `vdownload.hembed.com` 走 CDN77，它自己（CNAME 目标 `…rsc.cdn77.org`）不在黑名单上，而原域名在。换成真名后 `206 Partial Content / video/mp4` 连续 3/3 成功。*

CNAME 降级时 **Host 头必须跟着换**：

> *CDN77 校验 SNI 与 Host 一致，只换 SNI 不换 Host 会拿到 403（实测）。*

**候选去重的方向也有讲究**：

> *候选 = 该域名自己 DoH 解析出的 IP + 调用方给的 IP 池（去重）：IP 封锁常只封一批边缘 IP（如 hanime 观测到的那几个），目标域名的真实边缘 IP 可能恰好可达——只用一家的池等于把姊妹站也判死（javchu.com 实测：hanime 的池不通、自己 DoH 出来的 104.21.7.70 可达）。*

### 5.4 探测：并发 + 按延迟排序

```go
// 为什么不是"第一个成功的就用"：CDN 的边缘 IP 分布在不同地区，
// 先撞上的那个可能在美西（实测 CDN77 返回 `X-77-POP: sanjoseUSC`），
// 直接用会明显拖慢视频起播与拖动。并发探测的总耗时约等于最慢的一个，
// 但换来的是后续每一次请求都优先走最近的那个边缘——拨号期还会按序 failover。
func probeCandidates(ips []string, sni string, useECH bool) []string
```

`head(ips, 6)` 限制候选数，避免在注定超时的地址上耗掉整段探测时间。

### 5.5 拨号的三级降级（`dialOne`）

```text
1. ECH 握手
2. ECH 被拒且服务端给了 retry_configs → setECH + writeECHCache + 对同一 IP 重握一次
3. 仍失败 → 对同一 IP 降级普通 TLS（保护性降级：ECH 配置过期/轮换时至少保证连通）
4. 再失败 → 下一个 IP
```

第 3 步的注释措辞很准确：

> *保护性降级：普通 TLS（SNI 明文）。ECH 配置过期时至少保证连通；SNI 被针对的域名本来也无其它活路，失败会继续试下一个 IP。*

### 5.6 ECH 配置的四级来源

```go
func resolveECHConfig(flagB64, doh, domain string) ([]byte, string) {
    // 1. --ech-b64 flag
    // 2. 磁盘缓存（echCachePath，TTL = 1h）
    // 3. DoH 的 HTTPS 记录 → extractECHParam 取 ech="…" → base64 解码
    // 4. 内置 fallbackECHB64
}
```

缓存排在 DoH 之前是刻意的：

> *网关越早就绪，主页那批图片越可能赶上网关（实测图片本身没问题，之前失败是因为首页在网关就绪前就开始加载了）。*

TTL 定的 1 小时：

> *CF 会轮换密钥，缓存太久会拿到被服务端拒绝的过期配置（实测症状是 `tls: server rejected ECH`）。一小时是"启动够快"与"不会用到过期密钥"的折中。*

内置兜底也标了风险：

> *内置兜底：`cloudflare-ech.com` 的 HTTPS 记录实测值。公钥会轮换，仅供 DoH 也挂掉时顶一下——实测硬编码那份过期后会被服务端拒绝（`server rejected ECH`）。*

### 5.7 连接池与协议约束

```go
Transport: &http.Transport{
    DialTLSContext:        dialUpstream,
    MaxIdleConns:          512,
    MaxIdleConnsPerHost:   64,
    IdleConnTimeout:       90 * time.Second,
    TLSHandshakeTimeout:   10 * time.Second,
    ExpectContinueTimeout: 1 * time.Second,
    ForceAttemptHTTP2:     false,
}
```

> *连接池必须显式放大：默认值 `MaxIdleConnsPerHost=2` 意味着首页几十个并发请求里只有 2 条上游连接能复用，其余每次都要重新 TCP + ECH 握手（单次最坏 5s）。复用要成立还要求 `IdleConnTimeout` 长过一次浏览的间隔，30s 太短——用户看完一屏再滑，连接已经回收，下一屏又回到冷启动。*
>
> *`ForceAttemptHTTP2` 保持 false：出站 Transport 只会 HTTP/1.1（见 `dialOne` 里关于 `NextProtos` 的说明），协商出 h2 会拿到 "malformed HTTP response"。*

`NextProtos` 不能加 h2 也有对照说明：

> *参考实现加 h2 是因为它的出站 client 配了 `ForceAttemptHTTP2` + h2 传输。*

### 5.8 失败处理

```go
func onUpstreamError(w http.ResponseWriter, r *http.Request, err error) {
    log.Printf("echgate: upstream error origin=%s dial=%s ips=%v err=%v", ...)
    // 连不上多半是 IP 变了或策略过期，丢掉缓存让下次重新探测。
    invalidatePlan(origin)
    w.WriteHeader(http.StatusBadGateway)
    io.WriteString(w, "echgate: "+err.Error())
}
```

错误响应体带 **`echgate:` 前缀** —— 这是应用侧区分「网关自己的错误页」与「上游真的返回 502」的判据（`EchGateContract.ERROR_PAGE_PREFIX`）。

CLI 侧还有一处纵深防御：

```go
// 日志管道被启动方提前关闭时，写 stdout 不应以 SIGPIPE 杀死整个网关
// （Kotlin 侧 EchGateProcess 已为全生命周期排空，这是纵深防御）。
signal.Ignore(syscall.SIGPIPE)
```

---

## 六、网关生命周期

### 6.1 六态状态机（`EchGateStatus`，126 行）

```kotlin
sealed interface EchGateStatus {
    data object Idle; data object Starting
    data class Running(override val port: Int)
    data object Stopped; data class Failed(val reason: String); data object Exited

    fun onProcessOutputEnded(ownedByCurrentMonitor: Boolean = true): EchGateStatus = when {
        !ownedByCurrentMonitor -> this     // 主动停止 → 保持不变
        this is Stopped -> this
        else -> Exited
    }
    val port: Int get() = (this as? Running)?.port ?: -1   // 不在运行都是 -1（改写层据此零改动放行）
    val lastError: String? get() = when (this) { is Failed -> reason; Exited -> "网关进程已退出"; else -> null }
}
```

设计动机：

> *此前它们由三个互不相干的变量（`port`、`starting`、`lastError`）拼出来，于是"用户主动关"与"进程意外死亡"在类型上无法区分，设置页只能把两者都报成失败。*

**主动停止不得记成失败**是本状态的唯一硬规则，`onProcessOutputEnded(ownedByCurrentMonitor=false)` 就是它的载体：

> *主动停止（门面的 stop）会先把状态落成 `Stopped`、再回收运行时并清掉引用，于是这里拿到的 `owned` 为 false —— 预期内的停止不会变成 `Exited`，设置页也就不再报"网关失败：网关进程已退出"。*

### 6.2 门面（`EchGateRuntime`，264 行）

**① SingleFlight 就绪等待** —— 避免「首屏全白 8 秒」：

```kotlin
const val STARTUP_GRACE_MS = 8_000L
private const val HEAL_THROTTLE_MS = 30_000L

private val awaitingReady = AtomicBoolean(false)

fun awaitReadyIfStarting(timeoutMs: Long = STARTUP_GRACE_MS): Boolean {
    if (EchGate.port > 0) return true
    if (!SettingsRepository.useEchGate) return false       // 从未启动时不等
    if (!EchGate.starting) { /* 自愈：节流 30s 尝试拉起一次 */ if (!EchGate.starting) return false }
    if (!awaitingReady.compareAndSet(false, true)) return false   // ← 只放一个进来等
    try { awaitLeavingStarting(timeoutMs) } finally { awaitingReady.set(false) }
    return EchGate.port > 0
}
```

> *此前每个请求各自阻塞最多 8 秒：首页 API + 几十张封面同时到达时，几十个 OkHttp 分发线程一起卡在就绪信号上，用户看到的是"首屏全白 8 秒"。而探明网关是否就绪只需要一个请求 —— 其余应当立刻走兜底出口，能出的先出。*

**② 起服尾段的原子收尾**（与 `stop()` 共用一把 `ReentrantLock`）：

```kotlin
fun publishRunningIfStillStarting(port: Int): Boolean = readyLock.withLock {
    if (EchGate.status !is EchGateStatus.Starting) return@withLock false
    EchGate.publish(EchGateStatus.Running(port))
    true
}
```

> *起服是长阻塞的（桌面要探测上游 IP，Android 首次 DoH 最长 15 秒）。用户完全可能在这段窗口里关掉开关 —— 那时门面已落 `Stopped`，而起服器手里却攥着一个**已经起好**的运行时。**无条件 publish `Running` 会把"开关已关"覆盖成"正在运行"**，留下设置页与实际不一致的假就绪，且 Go 侧监听与 goroutine 会常驻到进程退出。*

**③ 平台通知钩子**（commonMain 没有跨平台 wait/notify）：

```kotlin
init {
    // commonMain 里没有跨平台的 wait/notify，把"叫醒等待者"接回本对象的信号上。
    // 骨架独占这条钩子：桌面/Android 的 starter 都不该再各自设一遍。
    EchGate.onStatusChanged = { readyLock.withLock { readyChanged.signalAll() } }
}
```

### 6.3 三端运行时（`EchGateStarter`）

| 平台 | 实现 | 行数 | 模型 |
|---|---|---|---|
| 桌面 | `DesktopEchGateStarter` | 274 | 解包 exe → `ServerSocket(0)` 选端口 → `echGateSeedIps()` → `ProcessBuilder` spawn（`redirectErrorStream(true)`，避免管道撑满）→ 读 stdout 的 `LISTENING` 行 |
| Android | `AndroidEchGateStarter`（**在 `:app` 壳**） | — | gomobile **进程内**起服（`app/libs/Echgate.aar`），就绪看 `Server.addr()` |
| iOS | Swift 壳 `EchGateBootstrap` | — | 起服后经 `EchGatePortReporter` 回填端口 |

Android 走进程内不是选择而是必然：

> *Android 无法执行应用私有目录里的可写文件（API 29+ 的 W^X），所以走不了桌面那条"解包 exe 再 spawn"的路；这里与 iOS 同模型、同一份 `gate.Start`。*

Android 落在 `:app` 而不是 `:shared` 的原因：

> *AAR 是 `:app` 的本地文件依赖（AGP 不允许 library 模块吃本地 `.aar`），于是引用 `gate.Gate` 的代码只能在应用壳里。这与 iOS 把起服放在壳工程（`EchGateBootstrap.swift`）是同一种分工：**壳负责起服，只有 `EchGate` 与门面对外**。*

**没装 starter 就等于「当前平台没有网关能力」**，与「包里没有产物」走同一条路径：落 `Failed` + 所有请求直连，行为与接入前完全一致。

### 6.4 线协议与诊断行（`EchGateContract`，61 行）

```kotlin
const val READY_PREFIX = "LISTENING"       // 网关取到 ECH 公钥配置并开始监听后才打印
const val ERROR_PAGE_PREFIX = "echgate:"   // 网关自己的上游错误页前缀
const val FLAG_LISTEN = "--listen"
const val FLAG_IP_LIST = "--ip-list"       // 上游 IP 种子：网关自己解析会撞上被污染的系统 DNS
const val FLAG_CF_HOSTS = "--cf-hosts"     // 只有这些域名才配用 CF IP + ECH
const val FLAG_CACHE_DIR = "--cache-dir"   // ECH 公钥配置磁盘缓存
```

收敛这些口令的动机：

> *这条边界是靠字符串维系的 …… 其中错误页前缀有两份副本：网关侧改一个字节，应用侧不会有任何编译错误，只会静默地"永远不认"。*

`DIAGNOSTIC_MARKERS` 保证网关的决策行不被 debug 级过滤掉：

> *网关的"为什么失败"全在这几行里（探测超时、拨号被拒、ECH 试不通改走 CNAME），只按 debug 级丢掉的话，客户端侧只剩一句"网关异常，回退直连"，排障没有依据。*

### 6.5 改写策略（`EchGatePolicy`，59 行）

```kotlin
const val GATE_HOST = "127.0.0.1"
const val TARGET_HEADER = "X-Ech-Target"

fun rewrite(rawUrl: String, port: Int): Rewrite? {
    if (port <= 0) return null                                     // 网关没跑 = 全放行
    val url = runCatching { Url(rawUrl) }.getOrNull() ?: return null  // 解析失败也放行
    if (url.protocol != URLProtocol.HTTPS) return null              // http 没有 SNI 可谈
    if (isBypassHost(url.host)) return null                         // 回环 / IP 字面量
    /* 协议换 http、host 换 127.0.0.1、port 换网关端口；保留 path/query */
}

fun isBypassHost(host: String): Boolean {
    if (host.isBlank()) return true
    val lower = host.lowercase()
    if (lower == GATE_HOST || lower == "localhost" || lower.endsWith(".local")) return true
    if (host.contains(':')) return true          // IPv6 字面量
    if (IPV4.matches(host)) return true
    return false
}
```

> *OkHttp 拦截器（jvmMain）、Ktor 插件（Darwin）、mpv/Exo/AVPlayer 的改写全部调这里——判定分叉正是"页面能开、视频打不开"类问题的根因。*

### 6.6 种子 IP（`EchGateUpstreamIps`，29 行）

```kotlin
fun echGateSeedIps(): List<String> =
    HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
        runCatching { HanimeDns.SHARED.preferredIps(host) }.getOrNull().orEmpty()
    }.distinct().ifEmpty { /* 退回各域系统解析结果 */ }
```

> *网关自己也会经 DoH 解析，但**国内 DoH 对被阻断域名会给出不可达的假 IP**——2026-10-02 Android 16KB 模拟器实测：AliDNS 把 `hanime1.me` 解析到 Facebook 段（`185.60.218.50`），网关拨号 15s 超时，整页打不开；桌面端因为一直传种子（探测过能建连的内置 IP）从未暴露这个问题。*

---

## 七、DNS：`HanimeDns` + `CdnIpProbe`

**文件**：`jvmMain/.../HanimeDns.kt`（336 行）、`jvmMain/.../CdnIpProbe.kt`（143 行）

### 7.1 四级降级

```text
① getchu 硬编码 IP（210.155.150.166 / 210.155.150.145）
② useBuiltInHosts（显式指定「我就是要走这些 IP」，不参与链条）
③ autoBuiltInHosts 自动档（CdnIpProbe 探测过的内置/自定义 IP）  ← 刻意排在 DoH 之前
④ DoH（+ 负缓存冷却）
⑤ 系统 DNS（与 DoH 并行发起，但 DoH 优先）
⑥ 内置/自定义 IP 兜底（只有 Hanime 系站点有数据）
全档皆墨 → 抛出系统解析那次的异常，保持 OkHttp 原有的失败语义
```

自动档排在 DoH 之前是实测结论：

> *实测 DoH 对这几个域名同样返回假 IP，让它抢先只是白跑一趟。*

**自动档默认开**的理由（`AppSettings.autoBuiltInHosts` KDoc）：

> *系统 DNS 对 `hanime1.me` / `www.hanime1.me` / `hanimeone.me` 全部返回**不可达**的假 IP（443 握手超时，`hanimeone.me` 甚至落到 Facebook 段），而内置 CF IP 实测 5 个里 3 个通（170–230ms）。不开这个，默认用户根本连不上站点。*
>
> *为什么不能直接把 `useBuiltInHosts` 强制档打开：那批 IP 会失效，强制档一旦失效就是断网，而它又没有回退。"探测 + 回退"才是可以默认打开的形态。*

### 7.2 DoH 负缓存

```kotlin
private const val DOH_FAILURE_THRESHOLD = 3
private const val DOH_COOLDOWN_MS = 30_000L
private const val SYSTEM_RACE_TIMEOUT_MS = 5_000L

private fun dohCooling(dohUrl: String, nowMs: Long): Boolean =
    dohCoolingUrl == dohUrl && nowMs < dohCooldownUntilMs
```

> *DoH 端点不可达时，每一档解析都要等满超时才降级系统解析，一个首屏会碰上若干个新域名，串行等待直接把首屏拖成十几秒。连续失败 3 次就整档摘掉 30 秒，期间请求零延迟走系统解析（正确性不变：那条路本来就是兜底）。*
>
> *冷却**按 URL 记**：用户换了 DoH 端点，新端点立刻不受旧冷却影响。计数用 `@Volatile` 而非原子类——它是"够不够糟糕"的启发式，并发下少记一次不影响结论，不值得为它加锁。*

### 7.3 系统与 DoH 的并行竞速（**不是** happy-eyeballs）

```kotlin
if (!dohUrl.isNullOrBlank() && !dohCooling(dohUrl, now)) {
    // 系统与 DoH 并行发起。
    //
    // 刻意**不是** happy-eyeballs 那种"谁先回用谁"：系统解析快但可能被污染，
    // DoH 慢但可信，采纳顺序必须仍是 DoH 优先。并行只省掉
    // "DoH 失败后再去查系统"那一段串行等待 —— 首屏碰上若干新域名时，
    // 这段等待会按域名数叠加。
    systemFuture = racePool.submit { Dns.SYSTEM.lookup(hostname) }
    val viaDoH = runCatching { lookupByDoH(dohUrl, hostname) }...
    if (viaDoH != null) return viaDoH
}
```

### 7.4 连通性探测（`CdnIpProbe`）

```kotlin
private const val PROBE_TIMEOUT_MS = 1200
private const val CACHE_TTL_MS = 10 * 60 * 1000L
private const val PROBE_PORT = 443
```

**为什么用 TCP 443 握手而不是 `InetAddress.isReachable`**：

> *后者在拿不到 raw socket 权限时（Windows 常见）退化成 **port 7 echo** 探测，对着 Cloudflare 边缘 IP 恒返回 false——拿它做筛选会把所有节点都判死，自动档就永远回退、等于没开。同一批 IP 用 443 握手则能拿到 170–230ms 的真实延迟。*

**为什么需要它**：

> *内置 IP 是硬编码的，会随时间失效。实测（2026-09-21 本机）5 个里只有 3 个通（170–230ms），另外 2 个 443 握手直接超时。把整张表原样交给 OkHttp 的后果不是"慢一点"，而是**先卡在两个死 IP 的连接超时上**——用户看到的就是首页一直转圈。*

**stale-while-revalidate**（TTL 到期不阻塞调用方）：

> *探测最坏 2.2s，而本函数跑在 OkHttp 的**网络线程**上。若 TTL 到期时同步等，命中过期那一屏的所有解析会一起卡满探测时间 —— 用户看到的是"滑到某一屏突然卡一下"。旧值即便有一两个失效，拨号期按序 failover 也能兜住，比整屏停住划算得多。*

**空表是有意为之**：

> *一个都不通时返回**空表**——这是有意的：调用方拿到空表就知道该回退到 DoH / 系统 DNS，而不是拿着一张全死的 IP 表去撞连接超时。*

### 7.5 SHARED 单例

```kotlin
/**
 * 全进程共用一份。
 *
 * DoH client 与自定义 IP 的解析结果都缓存在**实例字段**上，每处各 new 一个
 * 等于每处各建一个 DoH client、各存一份解析缓存。ServiceCreator / CDN 抓图 /
 * 下载 / 网关启动各有一处，此前是 5 份互不相干的缓存。
 */
val SHARED: HanimeDns = HanimeDns()
```

### 7.6 `preferredIps()` —— 让 CF 验证浏览器与 HTTP 层用同一个 IP

```kotlin
/**
 * 桌面 CF 验证浏览器靠它判断该不该用 `--host-resolver-rules` 把 host 钉住：
 * 钉到一个应用自己都不用的 IP 上，等于把验证页送到另一个出口，`cf_clearance` 照样绑不上。
 */
fun preferredIps(host: String): List<String>
```

`getCDNList()` 在本项目**已修掉参考项目的缺陷**（参考项目 DoH 开启时仍只走系统 DNS）——这里改为先走 `preferredIps()`，与真实解析路径一致。

---

## 八、Cloudflare 挑战

**文件**：`commonMain/.../CloudflareChallenges.kt`（131 行）、`androidMain/.../CloudflareVerificationCoordinator.kt`（113 行）

### 8.1 跨平台触发总线

```kotlin
object CloudflareChallenges {
    // replay=1：composition 前已发出的挑战不丢失（首页请求可能早于收集器注册）；
    // 已消费的事件不会重放给现有收集器，关闭后的 CF 页不会幽灵重开。
    private val _requests = MutableSharedFlow<CloudflareChallenge>(replay = 1, extraBufferCapacity = 1)
    val requests: SharedFlow<CloudflareChallenge> = _requests.asSharedFlow()

    private sealed interface Outcome { data class Solved(...); data class GivenUp(...) }
    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = 16)

    fun request(url: String)             // 触发验证页压栈
    fun passed(host: String)             // 由 clearance 写入口调用（SettingsRepository.setCloudFlareCookie）
    fun abandoned(host: String)          // 用户放弃（关窗 / 出栈而没通过）
    suspend fun awaitPassed(host, timeoutMs): Boolean
}
```

抽总线的动机（**参考项目只有 Android 能弹窗**）：

> *此前只有 Android 经 OkHttp 拦截器→`CloudflareVerifier`→Activity 跳转打开验证页；桌面（CDP 无头浏览器弹窗）/iOS（WKWebView 直嵌）的槽位实现虽已就绪（M5-5），但没有任何调用方把 `CloudflareRoute` 压栈，验证 UI 实际不可达——桌面/iOS 在 CF 挑战下登录与浏览直接去世（仅一个 toast）。*

**为什么结局也要广播**：

> *等信号的请求最怕的不是失败，是**没人再管它**。用户把验证窗关掉之后，请求要一直挂到超时才报错，界面就定格在转圈上。*

### 8.2 验证后自动续跑（`NetworkRepo.ioRequest`）

```kotlin
private const val CF_RETRY_WAIT_MS = 150_000L

private suspend fun ioRequest(request: suspend () -> HttpResponse, permitted: IntArray? = null): HttpResponse {
    val first = request()
    if (first.isSuccessfulSiteResponse(permitted)) return first
    try {
        first.throwRequestException()
    } catch (blocked: CloudflareBlockedException) {
        val host = CloudflareChallenges.hostOf(first.call.request.url.toString())
        if (!CloudflareChallenges.awaitPassed(host, CF_RETRY_WAIT_MS)) throw blocked
        val retried = request()
        if (!retried.isSuccessfulSiteResponse(permitted)) retried.throwRequestException()
        return retried
    }
}
```

> *为什么要在流里等：验证成功之前，失败的请求早就抛完了，所以"用户验完了、应用却什么都没发生"——只能手动退回再进，观感就是弹一次窗赌一把。这里让原请求挂在通过信号上，窗口一关页面自己就出来了。只续跑一次（再失败就照常报错），不给死循环留口子。*

超时值的选择：

> *等 CF 验证通过的上限：比桌面 CDP 的求解预算（120s）再宽一点，别卡在它前面放弃。*

### 8.3 clearance 判定

```kotlin
const val CF_CLEARANCE_NAME = "cf_clearance"

/**
 * 验证窗判定"过了"的唯一口径：必须见到精确的 [CF_CLEARANCE_NAME]。
 *
 * `cf_bm`（Bot Management 饼干）之类同前缀的不算 —— 拿它们当通过，
 * 请求带着去撞挑战只会再吃一次 403，表现即"验证过了还是不行"。
 */
fun hasClearance(names: Collection<String>): Boolean = CF_CLEARANCE_NAME in names

/**
 * 与 `AppSettings.cfCookieKeyFor` 同一规则：精确命中，或子域以后缀命中父域；
 * 单段父域（TLD，如 `me`）永不命中——否则一条 `me` 的记录会叫醒整棵 .me 树。
 */
internal fun outcomeMatchesHost(waitHost: String, outcomeHost: String): Boolean
```

**clearance 按 zone 存档**（`AppSettings.cfCookies: Map<String, String>`）：

> *为什么不是"一条 cookie + 一个 host"：Hanime1 有四个可切域名外加用户自定义镜像，而 clearance 是**按 zone 签发**的。单行存储下"在 B 域验证成功"会把 A 域那条还能用的凭据直接顶掉，用户看到的就是"切个镜像又弹一次验证窗、验完回来又弹"。*

### 8.4 UA 一致性（结构性约束）

```kotlin
/**
 * HTTP 层发送的 User-Agent（Android = 移动 UA，桌面 = 桌面 UA）。
 *
 * ⚠️ **它必须与 CF 验证浏览器用的是同一个字符串。**
 * `cf_clearance` 绑定 (出口 IP, UA)：浏览器解出来的 clearance 只有在服务端
 * 看到**同一个 UA** 时才有效。
 */
expect fun currentHttpUserAgent(): String
```

历史 bug 记录（2026-09-13 定位）：

> *桌面端 HTTP 层一直发的是移动 UA，而 `CloudflareCdp` 给验证浏览器塞的是桌面 UA —— 两者不一致 ⇒ 收割回来的 clearance 对应用请求**永远无效**，表现为"验证窗口走完流程、页面依旧打不开 / 反复弹验证"，而"手动粘贴 clearance"也一样无效（同样对不上 UA）。这一条与 headless 无关，是独立的结构性错误。*

改之前还实测验证过站点对 UA 不敏感（首屏 HTML 逐字节相同，219667 bytes，标记数 14/14、144/144 一致）。

桌面还有一条**不能伪造浏览器 UA** 的约束：

> *为什么不直接用常量：cf_clearance 绑定 UA，而浏览器 UA 又**不能伪造**（实测把真实 Chrome 153 伪装成 149 会永远卡在挑战页）。于是改成采集真实值存 `desktopBrowserUserAgent`，由 `currentHttpUserAgent()` 发给 HTTP 层。*

---

## 九、其他组件

### 9.1 Cookie（`HCookieJar`，87 行）

```kotlin
override fun loadForRequest(url: HttpUrl): List<Cookie> {
    // cf_clearance 只认持久化那一份：它由验证窗写入、由 403 作废。内存里再留一份就会
    // 绕过失效逻辑，让请求一直拿着死钥匙撞 403（表现即"验证过了还是不行"）。
    val cookies = mutableListOf<Cookie>()
    cookieMap[host]?.filterNot { it.name == CF_CLEARANCE_NAME }?.let { cookies.addAll(it) }
    cookies.addAll(loginCookies)
    SettingsRepository.cfCookieFor(host)?.let { ... }

    // 同键（name+domain+path）只发第一份：map 在前、DataStore 在后 =
    // 服务端取首份时响应带来的新鲜值优先；且无论哪一侧重复累积，发出字节数都有上界。
    val seen = HashSet<Triple<String, String, String>>()
    return cookies.filter { seen.add(Triple(it.name, it.domain, it.path)) }
}
```

`saveFromResponse` **合并而非覆盖** + **按名对账**：

> *loginCookie 对账（2026-10-09 全站 400 "Header Or Cookie Too Large" 的根因修复）：上面只对"本响应自带"的同键去重，而 DataStore 那份此前是无条件 append —— 每个响应攒一份完整 loginCookie，发出头线性增长直到 nginx 拒收。（旧 `removeAll { user_lang }` 只堵住了 user_lang 这一条名字，session 系照漏不误 —— 这正是此前"单名不爆、总量爆"。）*

日志**只打数量 / 键名 / 总字节**，不打值（凭据）：

> *值里是 `hanime1_session` / `XSRF-TOKEN` / `cf_clearance` 等凭据，原样打出来既刷屏（单行数 KB）又等于泄漏；字节数是下次"超限"最直接的证据。*

另有 `HCookieJarBoundsTest`（98 行）把"有界"固定成回归护栏。

### 9.2 代理（`HanimeProxySelector`，217 行）

| 特性 | 实现 |
|---|---|
| **回环永不代理** | `select()` 里对 `127.0.0.1` / `localhost` / `[::1]` / `::1` 直接返回 `Proxy.NO_PROXY` |
| **委托链解链** | `alternative` 构造时沿 `capturedAlternative` 解链，`MAX_UNWRAP_DEPTH = 8` |
| **惰性重绑** | `boundType` 记录绑定时类型，`select()` 里发现设置变了就 `updateProxy()` |
| **零 DNS** | `InetSocketAddress.createUnresolved(ip, port)` —— 保证 `select()` 内零 DNS、不阻塞、不抛 |
| **标准键 + legacy 键** | 两套都写（此前只写 legacy，JVM 根本不认） |
| **`connectFailed` 真转发** | 此前调 `select()` 又丢弃返回值，等于没通知 |

回环 bypass 的理由：

> *本地 ECH 网关就在本机：再交给外部代理，等于把流量绕出去又绕回来，而且绕出去的那一段是明文 SNI——正是我们要躲的东西。*

标准键修正的记录：

> *#issue-39: 代理沒有應用到 WebView 上，只能通過此種方式來全局代理。*
>
> *标准键（`http(s).proxyHost/Port`、`socksProxyHost/Port`、`http.nonProxyHosts`）：`DefaultProxySelector` 与 `HttpURLConnection` 真正读的（此前只写 `proxySet/proxyHost/proxyPort`，JVM 根本不认，所谓"全局代理"实际未生效）。*

### 9.3 重试（`RetryInterceptor`，104 行）

```kotlin
data class RetryDeadline(val deadlineNanos: Long)

class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseBackoffMs: Long = 300L,
    private val maxRetryBudgetMs: Long = 8_000L,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },     // 可注入 → 单测不真等
    private val jitterMs: () -> Long = { Random.nextLong(0L, 250L) },
) : Interceptor
```

**为什么要把预算 tag 下去**：

> *本拦截器挂在最外层，只在 **attempt 之间**检查预算；而内层 `EchGateInterceptor` 单次 attempt 里能跑三次往返（网关 → 网关重试 → 让位给代理），每次都可能等满 15s 连接超时 —— 叠加起来是分钟级的转圈，外层的 8s 预算根本管不到。*

**三层重试互不重叠**：

| 层 | 触发点 | 位置 |
|---|---|---|
| `RetryInterceptor` | `chain.proceed` **抛异常**（只看异常，永不看状态码） | 每条链最外层 |
| `EchGateInterceptor` | 网关 **502** + body 是网关错误页 | 网关步内 |
| `NetworkRepo.ioRequest` | **403 + CF 挑战** | flow 内 |

**刻意不做的两件事**：

> *- **非幂等方法一律不重试**：POST 重发有双提交风险（与网关侧同一条理由）。*
> *- **`UnknownHostException` 不重试**：域名类失败由 `HanimeDns` 的四段降级树负责，在 socket 层再重试只是白等一轮，且解决不了污染。*

**`SSLException` 在列**：

> *DPI 的 RST 打在 TLS 握手阶段时，抛出来的正是它（项目里"TCP 能握手、TLS 阶段必被 RST"那条实测就是这种表现）。代价是真的证书错误会多试两次——每次退避 300ms/600ms，可接受。*

### 9.4 CDN 抓取客户端（`CdnFetchClient`，76 行）

```kotlin
internal fun createCdnFetchClient(connectTimeoutSeconds: Long = 15L,
                                  extraInterceptors: List<Interceptor> = emptyList()): OkHttpClient
internal fun createThirdPartyClient(connectTimeoutSeconds: Long = 15L): OkHttpClient
```

抽工厂的动机：

> *「这些 client 必须与浏览/下载同一条出口」这条规矩，此前是靠人肉往每个 `OkHttpClient.Builder()` 里复制 `.dns()` / `.proxySelector()` / `.addInterceptor(EchGateInterceptor())` 维持的。它已经在 `CoverImageFetcher` 上失效过一次：那里只配了 DNS，于是开着 ECH 网关或手填代理时，下载任务的封面会走裸直连。*
>
> *复制粘贴的规矩拦不住人；函数 + `CdnFetchClientTest` 才拦得住。*

两个细节：

- **派生实例共享连接池**：`extraInterceptors` 非空时用 `base.newBuilder()` 而非从 `Builder()` 起新 client —— 否则「每个调用点各开一个连接池，首页几十张封面各自握一次 TLS，复用完全落空」
- **`createThirdPartyClient` 刻意不装 `EchGateInterceptor`**：「网关只对 `--cf-hosts` 点名的域名走 CF IP + ECH，第三方域名进去只是普通转发，多一跳且拿不到任何收益」

### 9.5 图片管线（`ImagePipeline`，29 行）

```kotlin
/**
 * 这里只剩 getchu 域名特化一件事了。
 *
 * ECH 改写与失败回退已收敛到 `EchGateClientPlugin` —— 图片与 API **共用同一个插件**，
 * 只是装配时关掉站点 Cookie（图片不该把登录态发给图床）。此前图片自己有一份
 * "只改写、不回退"的实现：网关一出问题整页封面全空，而同一时刻 API 链路靠
 * `EchGateInterceptor` 的回退照常工作，"页面能开、图全没了"就是这么来的。
 */
val GetchuBrandHeaders = createClientPlugin("GetchuBrandHeaders") {
    onRequest { request, _ -> /* 挂在 onRequest（早于网关插件的 on(Send)），看到的仍是原始 host */ }
}
```

### 9.6 播放器出口（`PlayerWiring`，90 行 + 三端 actual）

```kotlin
expect fun defaultPlayerNetworkConfig(): PlayerNetworkConfig   // 平台差异只在网络配置（UA / 代理）

/**
 * 网关改写：返回改写后 URL + 需附加的网关头；**不该用网关时返回 null**。
 *
 * 判定经 [EgressScheduler]：播放链路与 HTTP 链路共用同一份计划（含该域熔断、
 * 粘滞优选与强制模式）。此处只看端口的话，熔断期间媒体仍会被改写——那正是
 * "页面上已经好了、视频还在撞网关"的形态。
 */
internal fun gateRewrite(uri: String): Pair<String, Map<String, String>>?

/**
 * 播放网关链路结局 → [EgressReporter]。
 *
 * 视频 CDN 与图床常不同域（如 `vdownload.hembed.com → *.rsc.cdn77.org`），
 * 此前没有任何数据流进它的 [RouteRegistry] 健康，该域的网关出口**永不熔断**
 * ——正是"能浏览、不能播"的账本缺失。
 */
internal fun reportGateLoadOutcome(uri: String, ok: Boolean, reason: String?)
```

`MediaProxyResolver`（55 行）是**播放器出口判定的单份实现**，放 `jvmMain` 让 android 与 desktop 共用：

> *此前 `resolveMediaProxyUrl` 只在 desktopMain，android 侧因此写了第二份**判据不同**的实现（判"网关有没有在跑"而不是"这个 URL 是不是网关地址"），在"网关在跑但 URL 是真实源站"的回退分支上两者会给出相反结论。*

SOCKS 的处理是**宁可不设也不设错**：

> *SOCKS 返回 null 并打日志：FFmpeg 的 `http_proxy` 只支持 HTTP 代理（CONNECT 语义），把 `socks5://…` 塞进去只会让流更打不开。*

### 9.7 网络变化复位（`NetworkChangeReactions`，31 + 三端 actual）

```kotlin
fun onNetworkChanged() {
    RouteRegistry.reset()          // 各域的熔断与择优结论都隐含旧网络：一切网即清零
    platformOnNetworkChanged()     // JVM: 代理解析缓存 + CDN 探测缓存 + 空闲连接摘除 + DoH 冷却
}
```

> *最刺眼的是网关熔断：切到一条能用的网络后它还在冷却期（最长 5 分钟）里被挡着，用户体感是"换了网还是打不开"。*

三端接法：Android `registerDefaultNetworkCallback`（只认 `onAvailable`）/ iOS 壳 `NWPathMonitor` → `NetworkChangeBridge` / **桌面 JVM 没有网络变化事件，暂不接（已知限制）**。

### 9.8 CSRF（`CsrfTokenProvider`，59 行）

提供 `IHCsrfToken` 抽象 + 站点 CSRF token 的提取与缓存（登录、评论、收藏等写操作依赖）。

---

## 十、异常与错误语义

| 异常 | 触发点 | 处理 |
|---|---|---|
| `NoRouteException`（继承 `okio.IOException`） | 调度表为空 | 翻成一句人话 + 设置深链（配代理 / 检查网关开关 / 走验证），**不重试**（`shouldRetryDarwinFailure` 显式排除） |
| `IPBlockedException` | 403 + body 含 `you have been blocked` | 展示 IP 被封文案 |
| `CloudflareBlockedException` | 403 + `cf-mitigated: challenge` / body `Just a moment` | **触发验证窗 + 请求挂在通过信号上续跑** |
| `HanimeNotFoundException` | 403 其他 / 500 | 「视频可能不存在」 |
| 登录链路专用判定 | `throwIfCloudflareBlocked(response)` | 无标记 403/404 兜底也当 CF（桌面无 OkHttp CF 拦截器时的补偿）；419（CSRF 过期）/422/5xx 走原逻辑不开窗 |
| 未知状态码 | `throwRequestException` else 分支 | **打 body 前 300 字符再抛** —— 「否则像 `loadReplies` 的 400 这种只能看到光秃秃的 "400 "」 |

`throwRequestException` 的 403 分支顺序有讲究：

> *CF 触发单点：header 路径（原 Android 拦截器认 `cf-mitigated: challenge`）与 body 标记路径在此会合，同一总线、同一等待、同一续跑。顺序在 body 之前：边缘直接拒时 body 可能为空，原先会掉进下面的"空 body 抛错"分支。*

---

## 十一、已知缺口与风险

### 11.1 代码注释里自陈的

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **iOS 手填代理不生效** | Darwin 引擎无按请求配代理的口子（`ProxyCapability.ios`）；「已知缺口，Phase 4 处理，计划里照排不撒谎」 |
| 2 | **iOS 网关种子 IP 为空列表** | `echGateSeedIps` KDoc：「iOS 壳目前仍是空列表，属已知差异」 |
| 3 | **桌面无网络变化事件** | JVM 没有对应 API；`onNetworkChanged` 靠 Android/iOS 触发 |
| 4 | **iOS 的 AVURLAsset 分片请求不经过 Ktor/Darwin** | m3u8 分片的 403 无法被 `ioRequest` 续跑，需 `AVAssetResourceLoaderDelegate` 专项（已标注另立项） |
| 5 | **预算不能在途抢占** | 「在途 IO 不能被抢占 —— 这是 OkHttp 同步链的固有限制，不撒谎」 |

### 11.2 结构性的（本次阅读新增观察）

| # | 观察 | 影响 |
|---|---|---|
| 6 | **两个执行器是并行实现** | `EchGateInterceptor`（347 行）与 `EchGateClientPlugin`（305 行）判定完全共享，但执行骨架（`GateStep` 密封接口、让位定责、预算自查、`describeEmpty`、`restoreOriginal`）各抄一份，约 150 行高度相似。三处差异是**刻意的**（见 §4.3），但结构性重复仍会在改判定时埋下分叉风险 —— 这正是 `EchGatePolicy` KDoc 反复警告的那类问题 |
| 7 | **`HCookieJar.cookieMap` 仍是 companion 静态全局容器** | 有界去重已补（`HCookieJarBoundsTest` 固定），但容器未换（如按 host 分段 + 容量上界）；且进程内跨测试共享 |
| 8 | **`HanimeDns` 的 `racePool` 与 `CdnIpProbe.probePool` 是两套线程池** | 都是 daemon cache pool，可接受；但 `racePool` 无上界，极端并发下会起较多线程 |
| 9 | **网关是单实例约束** | `gate` 包级状态（`plans` / `echBytes` / `cfIPs`…），「一进程只调一次 `Start`」。桌面解包到用户目录若残留多个进程，第二个会监听失败（已由 `listen failed` 报错，但提示不够友好） |

---

## 十二、与参考项目 `Han1meViewer` 的逐维对照

| 维度 | `Han1meViewer`（参考） | `LoveHan1me`（本项目） |
|---|---|---|
| **ECH 实现** | Conscrypt 反射契约（`PolicyTrustManager.getNetworkSecurityPolicy`）+ `EchSocketFactory` 手动 `setEchConfigList` | Go 网关侧 `tls.Config.EncryptedClientHelloConfigList` |
| **ECH 配置来源** | `EchDoh` 手写 DNS wire 查询 + 手解 SVCB/HTTPS 记录（key 5）+ ASN 校验（Team Cymru） | 网关侧 DoH JSON 查询 + `extractECHParam` 正则取 `ech="…"`（**简化**） |
| **QUIC / H3** | Rust quiche 0.22 静态链入 `libchino.so`，`H3Interceptor` 对静态资源改走 UDP QUIC | **不做 QUIC**；改用 CNAME 真名（`alias`）策略解决 CDN77 场景 |
| **TLS 栈** | Android Conscrypt（**仅 TLS 1.3**） | Go `crypto/tls`（`MinVersion = TLS 1.2`，ECH 时升 1.3） |
| **出口选择** | 无；ECH 是全局开关 | **按域调度**：熔断 + EWMA 择优 + 粘滞锁定 + 手动逃生舱 |
| **失败策略** | fail-closed（核心域）/ 明文降级，写在 `EchTransportPolicy` | **按域记账 + 让位定责**；网关挂了全链路零影响 |
| **WebView** | **三层接管**（`shouldInterceptRequest` + JS hook fetch/XHR/form + `@JavascriptInterface` 桥） | 走 `GateWebViewProxy`（androidMain）；不做 JS 层 hook |
| **CF 验证** | Activity 弹窗 + `CountDownLatch`（仅 Android） | **跨平台总线**（SharedFlow）+ **验证后自动续跑** + 按 zone 存档 clearance |
| **配置热切换** | `rebuildOkHttpClient()` 重建 3 个 client | **稳定单例**，设置每请求实时读；仅网关需要 start/stop |
| **Retrofit** | 使用（`Response<ResponseBody>`） | **已删除**，全 Ktor |
| **DNS** | `HDns` 三级降级 + `DohConfig` 四预设 | `HanimeDns` 四级降级 + **DoH 负缓存** + **连通性探测自动档** |
| **可测性** | 依赖真实网络 | **判定全纯函数**，25 个网络测试文件 / 4,219 行 |
| **签名校验** | `chino.cpp` 手写 `svc` 系统调用读 APK 签名块 + 自实现 SHA-256 | （本项目未做，或已移除 —— 本次分析未在 `:app` 见到对应实现） |

**继承关系**：`ServiceCreator` / `HCookieJar` / `HanimeProxySelector` / `HanimeDns` 的骨架源自上游（作者署名保留 `Yenaly Liew`，见 NOTICE），但都做了实质性改造（稳定单例 / 有界去重 / 标准代理键 / 负缓存与探测）。

---

## 附录 A：文件清单

### A.1 `commonMain`（30 文件 / 3,453 行）

```text
data/NetworkRepo.kt                                       844   仓库层（flow 三件套 + CF 续跑）
data/network/
├── EchGatePlugin.kt                                      305   Ktor 执行器（Darwin）
├── service/HanimeMyListService.kt                        291
├── egress/EgressStatus.kt                                184   三态与导出（纯函数）
├── egress/EgressScheduler.kt                             176   调度器（纯函数判定）
├── service/HanimeCommentService.kt                        132
├── CloudflareChallenges.kt                                131   CF 触发总线
├── EchGate.kt                                             126   六态状态机
├── egress/EgressTypes.kt                                  123   共享类型 + 纯函数判定
├── egress/SchedulerModels.kt                              117   RouteId / Purpose / 预算
├── egress/RouteHealth.kt                                  116   熔断 + EWMA + 粘滞
├── service/HanimeBaseService.kt                           113
├── service/GetchuService.kt                               103
├── PlayerWiring.kt                                         90   播放器出口判定与记账
├── egress/EgressReporter.kt                                74   上报（健康 + 事件）
├── EchGateContract.kt                                      61   线协议口令
├── EchGatePolicy.kt                                        59   改写策略（枢纽）
├── CsrfTokenProvider.kt                                    59
├── DohConfig.kt                                            58
├── egress/RouteRegistry.kt                                 44   各域健康唯一持有者
├── egress/DomainClass.kt                                   44
├── service/HanimeSubscriptionService.kt                    38
├── egress/NetworkChangeReactions.kt                        31
├── HanimeNetwork.kt                                        30
├── ImagePipeline.kt                                        29
├── HttpClientFactory.kt                                    21
├── egress/NoRouteException.kt                              17
├── egress/EgressExport.kt                                  17
├── HProxyTypes.kt                                          15
└── IHCsrfToken.kt                                           5
```

### A.2 `jvmMain`（android + desktop 共享，17 文件 / 1,953 行）

```text
data/network/
├── interceptor/EchGateInterceptor.kt                      347   OkHttp 执行器
├── HanimeDns.kt                                           336   四级降级 + DoH 负缓存
├── EchGateRuntime.kt                                      264   门面（SingleFlight / 自愈 / 原子收尾）
├── HanimeProxySelector.kt                                 217
├── ServiceCreator.kt                                      160   三个稳定单例
├── CdnIpProbe.kt                                          143   连通性探测 + stale-while-revalidate
├── interceptor/RetryInterceptor.kt                        104
├── HCookieJar.kt                                           87
├── CdnFetchClient.kt                                       76
├── MediaProxyResolver.kt                                   55
├── NetworkPlatform.kt                                      34   （expect 声明）
├── EchGateUpstreamIps.kt                                   29
├── interceptor/UserAgentInterceptor.kt                     24
├── egress/NetworkChangeReactions.jvm.kt                     21
├── egress/ProxyCapability.jvm.kt                            20
├── interceptor/GetchuInterceptor.kt                         19
└── interceptor/UrlLoggingInterceptor.kt                     17
```

### A.3 `androidMain` / `iosMain` / `desktopMain`（16 文件 / 993 行）

```text
androidMain/.../data/network/            (4 文件 / 193 行)
├── CloudflareVerificationCoordinator.kt                   113   按 host 去重 + CountDownLatch
├── PlayerWiring.android.kt                                 46
├── NetworkPlatform.android.kt                              20
└── HttpClientFactory.android.kt                            14

iosMain/.../data/network/                (8 文件 / 332 行)
├── IosCookieBridge.kt                                     117   CF 验证产物进入 HTTP 层
├── HttpClientFactory.ios.kt                                56
├── EchGate.ios.kt                                          52
├── EchGatePortReporter.kt                                  40
├── PlayerWiring.ios.kt                                     33
├── egress/NetworkChangeBridge.kt                           14
├── egress/ProxyCapability.ios.kt                           12
└── egress/NetworkChangeReactions.ios.kt                     8

desktopMain/.../data/network/            (4 文件 / 468 行)
├── DesktopEchGateStarter.kt                               274   解包 + spawn + 读就绪行
├── PlayerWiring.desktop.kt                                144
├── NetworkPlatform.desktop.kt                              36
└── HttpClientFactory.desktop.kt                            14
```

另有 `app/src/main/kotlin/lovehan1me/echgate/AndroidEchGateStarter.kt`（Android 进程内起服，落在 `:app` 因为 AGP 不允许 library 模块吃本地 `.aar`）。

### A.4 `echgate`（Go，3 文件 / 970 行 + 3 个构建脚本）

```text
echgate/
├── gate/gate.go                                            846   核心：策略 / 探测 / 拨号 / DoH / ECH
├── main.go                                                  95   CLI
├── gate/mobile.go                                           29   gomobile facade（扁平签名）
├── build.sh                                                 26   桌面三平台交叉编译
├── build-android.sh                                         55   → app/libs/Echgate.aar
└── build-ios.sh                                             15   → iOS framework
```

### A.5 测试（25 文件 / 4,219 行）

```text
desktopTest（11 文件 / 3,003 行）
├── EchGateInterceptorTest.kt                              668
├── EgressChainTest.kt                                     423   端到端链路
├── EchGateRuntimeTest.kt                                  385   六态状态机 + 就绪等待
├── CloudflareClearanceTest.kt                             370
├── EchGateLiveTest.kt                                     332   真实网络
├── EchGatePluginTest.kt                                   309
├── NetworkChangeReactionsTest.kt                          126
├── RetryInterceptorTest.kt                                120
├── CdnFetchClientTest.kt                                  104
├── HCookieJarBoundsTest.kt                                 98   有界性回归护栏
└── HanimeServiceBaseUrlTest.kt                             68

commonTest（14 文件 / 1,216 行）
├── egress/scheduler/EgressSchedulerTest.kt                241
├── egress/EgressStatusTest.kt                             151
├── egress/scheduler/RouteHealthTest.kt                    118
├── PlayerGateOutcomeTest.kt                               104
├── EchGateStatusTest.kt                                    80
├── EchGatePolicyTest.kt                                    78
├── egress/scheduler/DomainClassifierTest.kt                75
├── EchGateContractTest.kt                                  73
├── egress/scheduler/ReporterRegistryTest.kt                68
├── CsrfTokenProviderTest.kt                                67
├── egress/scheduler/EgressExportTest.kt                    49
├── DarwinRetryPredicateTest.kt                             42
├── CloudflareOutcomeMatchTest.kt                           41
└── CloudflareChallengesTest.kt                             29
```

---

## 附录 B：网络配置项清单

来源：`core/domain/model/AppSettings.kt`（映射读口在 `data/SettingsRepository.kt:203-222`）

| 字段 | 默认值 | 用途 |
|---|---|---|
| `domainName` / `selectedBaseUrl` | `https://hanime1.me/` | 主站域名 |
| `useCustomMirrorSite` / `customMirrorSite` / `appendCustomMirrorPath` | `false` / `""` / `true` | 自定义镜像站 |
| `loginCookie` | `""` | 持久化登录 Cookie |
| **`cfCookies`** | `emptyMap()` | **按 zone 存档的 `cf_clearance`（host → Cookie 头）** |
| `isAlreadyLogin` / `savedUserId` | `false` / `""` | 登录态 |
| `useBuiltInHosts` | `false` | 强制走内置/自定义 IP（**无回退**，需重启生效） |
| **`autoBuiltInHosts`** | **`true`** | **自动档：先探测可建连的 IP，全不通才回退 DoH/系统** |
| `customHostsData` | `""` | 逗号分隔的自定义 IP |
| `useDoH` | `false` | 启用 DoH |
| `dohPreset` | `"alidns"` | 预设（`alidns` / `dnspod` / `cloudflare` / `ech_gateway` / `custom`） |
| `dohCustomUrl` | `""` | 自定义 DoH URL |
| `dohBootstrapIps` | `""` | DoH 引导 IP |
| `dohTimeoutSeconds` | `10` | `coerceIn(1, 60)` |
| **`useEchGate`** | **`true`** | **启用本地 ECH 网关（默认开，失败自动降级）** |
| **`egressForceMode`** | **`"Auto"`** | **调度器逃生舱：`Auto` / `ForceGate` / `ForceDirect` / `ForceProxy`** |
| `proxyType` | `ProxyType.System` | Direct / System / HTTP / SOCKS |
| `proxyIp` / `proxyPort` | `""` / `-1` | 代理地址 |
| `desktopBrowserUserAgent` | `""` | **桌面 CF 验证浏览器自报的真实 UA**（`cf_clearance` 绑定 UA，不能伪造） |
| `useBackupMediaCdn` | — | 媒体 CDN 换 CDN77（参考项目保留的机制） |

`useEchGate` 默认开的理由：

> *项目的目标是免梯直连，网关失败时所有改写层自动放行、走原有机制（代理 / 内置 hosts / DoH），与关闭行为一致，故默认开是安全的。用户手动关掉后予以尊重，不再自愈拉起。*

---

## 附录 C：架构评价

### 做得好的三件事

1. **把不可测的东西变成可测的**——`EgressScheduler` / `RouteHealth` / `EchGatePolicy` / `EgressStatus` 全是纯函数，25 个网络测试文件里有 9 个直接测它们。参考项目的同类逻辑散在 `ConscryptEch` 的私有方法里，只能靠真实网络验证。
2. **每个「为什么」都留了证据**——注释普遍带日期 + 实测数据 + 反例（"2026-09-21 本机实测 5 个里只有 3 个通"、"CDN77 返回 `X-77-POP: sanjoseUSC`"、"拿到 403（实测）"）。这让后续维护者能判断结论是否仍然成立，而不是盲信。
3. **主动删除错误抽象**——`EgressBudgets` 那个从未被读的 `route` 参数、"三个互不相干的变量"拼出的状态、折叠掉的三条假路由，都是「减法」而非「加法」。注释里明确写了「不要把它加回来」。

### 值得关注的两件事

1. **两个执行器的结构性重复**（§11.2 #6）——判定已经统一，但执行骨架仍是两份。若判定规则继续演进，两份骨架的差异会累积。可考虑把 `GateStep` 状态机与让位定责抽成引擎无关的协程流程，两个执行器只提供 `proceed` 与 `report` 两个原语。
2. **`EchGateInterceptor` 的三个"洞"是改造带来的固有成本**——Cookie 按原域名取、Set-Cookie 按原域名存、403 让位定责。这三处都在一份 347 行的类里，且每处都有详细的注释说明"为什么不能省"。这是**代理式改写**的必然代价，换任何实现都躲不掉；把它保持在一处（而不是分散到各 client）是当前最合理的做法。
