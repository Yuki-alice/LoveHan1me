# Han1meViewer 网络架构分析

> **分析对象**：`reference/Han1meViewer`（Android / Kotlin 2.3 + Jetpack Compose，`applicationId = io.github.daisukikaffuchino.han1meviewer`）
> **分析日期**：2026-10-10
> **分析方式**：全量静态阅读 —— `logic/network/**`、`logic/{NetworkRepo,GetchuNetworkRepo,Parser}.kt`、`app/src/main/cpp/**`、`native-h3/**`、`buildSrc/EchH3.kt`、`app/build.gradle.kts`、`app/proguard-rules.pro`
> **文档定位**：为 LoveHan1me（KMP 移植）提供网络层对标基线；每条结论均附文件路径
> **注意**：`reference/` 下同时存在 `Han1meViewer` 与 `Han1meViewer-main` 两份镜像。**本文档只覆盖 `Han1meViewer`**（新版，进程内 ECH 方案）；`Han1meViewer-main` 是旧路线（Go `echproxy` AAR 起本地代理），已在 §12 说明差异
> **本机文件**：`docs/` 已被 gitignore，本文档不入库

---

## 一、总体结构

### 1.1 分层图

```text
        UI / ViewModel ── WebView 验证页 ── 播放器 / 下载
                                │
                                ▼
        NetworkRepo / GetchuNetworkRepo   （flow 三件套，产出三种状态）
                                │
                                ▼
        HanimeNetwork ── 5 个 Retrofit Service（Response<ResponseBody>）
                                │
                                ▼
        ServiceCreator ── hClient / downloadClient / getchuClient
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
            HDns 直连链              echTransport ECH 链
        （内置 IP 池 · DoH · 系统）  （Conscrypt TLS + QUIC/H3）
                    └───────────┬───────────┘
                                ▼
              系统网络：Socket / TLS 1.3 / UDP (QUIC)
```

### 1.2 架构意图

三处解耦，使得「改一个设置 = 全链路热切换」：

| 解耦点 | 由谁完成 | 效果 |
|---|---|---|
| client 装配 ↔ 传输选择 | `ServiceCreator.applyHTransport()` | 一个布尔开关换掉整条 TLS 栈 |
| HTTP 结果 ↔ 业务状态 | `NetworkRepo` 三个 flow 包装器 | UI 只认 `WebsiteState` 等三种状态，不认 HTTP |
| TLS 链路整体可替换 | `echTransport()` 扩展函数 | `useEch` 开关一次性换掉 4 个组件 |

切换只需三步（`ui/navigation/settings/NetworkSettingsRoute.kt`）：

```kotlin
EchHttp.onDohSettingsChanged()   // 清空 ECH / DoH 全部缓存
HanimeNetwork.rebuildNetwork()   // 重建 3 个 client + 重新 create Retrofit
SingletonImageLoader.reset()     // Coil 换 client
```

### 1.3 相关模块与线上依赖

| 层 | 技术 |
|---|---|
| HTTP | Retrofit 3 + OkHttp + `okhttp-dnsoverhttps` |
| 序列化 | 全部接口返回 `Response<ResponseBody>`，**不用 converter**，交 `Parser` 处理 |
| HTML 解析 | Jsoup（`Parser.kt` / `GetchuParser.kt`） |
| TLS/ECH | Conscrypt Android（`libs.conscrypt.android`） |
| QUIC | Rust quiche 0.22（vendored），静态链入 `libchino.so` |
| 图片 | Coil 3 + `coil-network-okhttp` |
| 播放 | Media3 ExoPlayer / MPV / 系统播放器 |
| 后台 | WorkManager |

---

## 二、Client 层：ServiceCreator

**文件**：`app/src/main/java/io/github/daisukikaffuchino/han1meviewer/logic/network/ServiceCreator.kt`（111 行）

### 2.1 三个 OkHttpClient

| Client | 用途 | connectTimeout | 拦截器 | 其他 |
|---|---|---|---|---|
| `hClient` | 主站 API / 图片 / WebView 桥 | 15s | `UserAgentInterceptor` → `UrlLoggingInterceptor` → `CloudflareInterceptor` | `Cache` 10 MB（`cacheDir/http_cache`）、`HCookieJar`、`HProxySelector`、`applyHTransport()` |
| `downloadClient` | 下载 Worker | 5s | `UserAgentInterceptor` → `SpeedLimitInterceptor` | **`protocols(listOf(Protocol.HTTP_1_1))`**、`applyHTransport()` |
| `getchuClient` | Getchu 资料站抓取 | 15s | `UrlLoggingInterceptor` → `GetchuInterceptor` | `CookieJar.NO_COOKIES`、`HProxySelector`、**`dns(dns)` 不走 ECH** |

三者的共性：**都没有配 `readTimeout`**（用 OkHttp 默认 10s）；`getchuClient` 是唯一显式不走 ECH 的 client。

```kotlin
private fun buildHClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(UserAgentInterceptor)
    .addInterceptor(UrlLoggingInterceptor())
    .addInterceptor(CloudflareInterceptor(applicationContext))
    .cache(cache)
    .cookieJar(HCookieJar())
    .proxySelector(HProxySelector())
    .applyHTransport()
    .build()
```

### 2.2 运输开关

```kotlin
// ServiceCreator.kt:71-72
private fun OkHttpClient.Builder.applyHTransport(): OkHttpClient.Builder =
    if (SettingsRepository.useEch) echTransport(dns) else dns(dns)
```

`dns` 是 `ServiceCreator` 内的单例 `private val dns = HDns()`。

### 2.3 六个拦截器职责

| 拦截器 | 位置 | 行为 |
|---|---|---|
| `UserAgentInterceptor` | `interceptor/` | `addHeader("User-Agent", USER_AGENT)`，固定伪装 Chrome 149 Mobile |
| `UrlLoggingInterceptor` | `interceptor/` | `URLDecoder.decode` 后打印完整 URL（含查询参数） |
| `CloudflareInterceptor` | `interceptor/` | 见 §5 |
| `SpeedLimitInterceptor` | `interceptor/` | 把 `response.body` 换成 `SpeedLimitResponseBody` |
| `SpeedLimitResponseBody` | `interceptor/` | 用 okio `Throttler().bytesPerSecond(maxSpeed)` 包 source；`maxSpeed == 0` 表示不限速 |
| `GetchuInterceptor` | `interceptor/` | 换桌面 UA + `Referer: https://www.getchu.com/` + `Cookie: getchu_adalt_flag=getchu.com; gc=gc` + `Accept-Language: ja` |

限速档位在 `logic/model/AppSettings.kt:3-13`：

```kotlin
val DOWNLOAD_SPEED_BYTES = longArrayOf(
    0L, 128*1024L, 256*1024L, 512*1024L, 1024*1024L,
    2048*1024L, 4096*1024L, 8192*1024L, 10240*1024L,
)
```

`SpeedLimitInterceptor` 持有可变 `var maxSpeed`（`unsafeLazy` 初始化自 `SettingsRepository.downloadSpeedLimit`），因此限速可运行时调整。

### 2.4 热重建

```kotlin
// ServiceCreator.kt:53-69
var hClient: OkHttpClient = buildHClient()   private set
var downloadClient: OkHttpClient = buildDownloadClient()   private set
var getchuClient: OkHttpClient = buildGetchuClient()   private set

fun rebuildOkHttpClient() {
    hClient = buildHClient()
    downloadClient = buildDownloadClient()
    getchuClient = buildGetchuClient()
}
```

注意 `hClient` 等是 **`var` + `private set`**，因此外部只能通过 `rebuildOkHttpClient()` 换代。

### 2.5 服务聚合：HanimeNetwork

**文件**：`logic/network/HanimeNetwork.kt`（50 行）

```kotlin
object HanimeNetwork {
    var hanimeService = _hanimeService           private set
    var getchuService = _getchuService           private set
    var commentService = _commentService         private set
    var myListService = _myListService           private set
    var subscriptionService = _subscriptionService   private set

    private val _hanimeService get() = ServiceCreator.create<HanimeBaseService>(HANIME_BASE_URL)
    // ... 其余 4 个同理

    fun rebuildNetwork() {
        ServiceCreator.rebuildOkHttpClient()
        hanimeService = _hanimeService
        getchuService = _getchuService
        commentService = _commentService
        myListService = _myListService
        // ⚠️ subscriptionService 未重新赋值 —— 见 §11 缺陷 1
    }
}
```

`HANIME_BASE_URL` / `GETCHU_BASE_URL` 定义在 `Constants.kt`：

```kotlin
val HANIME_BASE_URL: String get() = SettingsRepository.baseUrl   // 动态读设置
const val GETCHU_BASE_URL = "https://www.getchu.com/"
```

五组接口（`logic/network/service/`）：

| Service | 端点 | 说明 |
|---|---|---|
| `HanimeBaseService` | `@GET` / `search` / `watch` / `previews/{date}` / `login` | 首页、搜索、视频详情、预览、登录 |
| `HanimeCommentService` | 评论相关 | |
| `HanimeMyListService` | 收藏 / 播放列表 / 在线历史 / 账号 | 含头像上传 `MultipartBody` |
| `HanimeSubscriptionService` | `subscriptions` | 订阅作者 |
| `GetchuService` | `all/month_title.html` / `item/{id}/` / AJAX | Getchu 预览与系列 |

全部方法签名为 `suspend fun ...(...) : Response<ResponseBody>`。

---

## 三、DNS 层：HDns + DohConfig

**文件**：`logic/network/HDns.kt`（212 行）、`logic/network/DohConfig.kt`（70 行）

### 3.1 HDns 三级降级

`override fun lookup(hostname)` 的优先级：

| 顺序 | 条件 | 行为 |
|---|---|---|
| 1 | `hostname == "www.getchu.com"` | 硬编码 `210.155.150.166` / `210.155.150.145` |
| 2 | `useBuiltInHosts && HANIME_HOSTNAME.contains(hostname)` | 用户自定义 IP 列表，为空则用内置 Cloudflare IP 池 |
| 3 | `DohConfig.resolveUrl()` 非空 | DoH 解析；**失败回落到 `Dns.SYSTEM`** |
| 4 | 兜底 | `Dns.SYSTEM.lookup(hostname)` |

内置 IP 池（`HDns.kt:34-37`）：

```kotlin
private val cloudFlareIps = listOf(
    "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1",
    "2606:4700:3035::ac43:bb8d", "2606:4700:3030::6815:746", "2606:4700:3030::6815:714"
)
```

主机名白名单（`Constants.kt:60`）：

```kotlin
val HANIME_HOSTNAME = arrayOf("hanime1.me", "hanime1.com", "hanimeone.me", "javchu.com")
val HANIME_URL      = arrayOf("https://hanime1.me/", "https://hanime1.com/", "https://hanimeone.me/", "https://javchu.com/")
val ANIME_URL       = arrayOf(...)   // 前三项，无 javchu
```

### 3.2 DoH 客户端构造与缓存

```kotlin
// HDns.kt:140-171 —— 双检锁 + 配置比对缓存
private var cachedDohConfig: DohRuntimeConfig? = null
private var cachedDohDns: Dns? = null

private fun getOrCreateDohDns(config: DohRuntimeConfig): Dns {
    val currentDns = cachedDohDns
    if (currentDns != null && cachedDohConfig == config) return currentDns
    synchronized(this) { /* 二次检查 → 构造 DnsOverHttps */ }
}
```

`DnsOverHttps.Builder` 的固定参数：`includeIPv6(true)`、`post(false)`、`resolvePrivateAddresses(true)`、`resolvePublicAddresses(true)`，并在 `bootstrapIps` 非空时设 `bootstrapDnsHosts(...)`。

另有两个独立缓存（`@Volatile` 字段 + raw 比对）：

```kotlin
private var cachedCustomIps: List<String>? = null
private var cachedCustomIpsRaw: String? = null
```

### 3.3 DohConfig 预设

```kotlin
val presets = listOf(
    DohPreset("alidns",     "AliDNS",              "https://dns.alidns.com/dns-query",        listOf("223.5.5.5","223.6.6.6")),
    DohPreset("dnspod",     "DNSPod",              "https://doh.pub/dns-query",               listOf("1.12.12.12","120.53.53.53")),
    DohPreset("cloudflare", "Cloudflare",          "https://cloudflare-dns.com/dns-query",
              listOf("1.1.1.1","1.0.0.1","2606:4700:4700::1111","2606:4700:4700::1001")),
    DohPreset("ech_gateway","小雅DoH (ECH/H3)",   "https://tgxjjdszvu.cloudflare-gateway.com/dns-query", echGatewayBootstrapIps),
)
```

第四个预设的 bootstrap IP 是**随机化**的（`SecureRandom` 从 `172.64.229.4` ~ `172.64.229.250` 抽 4 个）：

```kotlin
private val echGatewayBootstrapIps: List<String> by lazy {
    val random = SecureRandom()
    (4..250).shuffled(random).take(4).map { "172.64.229.$it" }
}
```

### 3.4 互斥约束

`useBuiltInHosts` 与 `useDoH` **互斥**，设置页强制二选一（`NetworkSettingsRoute.kt:445-467`，`DohConflictTarget` 枚举区分触发方向）：

```kotlin
DohConflictTarget.EnableDoH          -> it.copy(useBuiltInHosts = false, useDoH = ...)
DohConflictTarget.EnableBuiltInHosts -> it.copy(useDoH = false, useBuiltInHosts = true)
```

原因：内置 hosts 直接跳过 DNS 环节，与 DoH 语义冲突。

### 3.5 辅助 API

| 方法 | 用途 |
|---|---|
| `parseCustomIps(raw)` | 逗号分隔解析自定义 IP |
| `validateCustomHosts(raw)` | 返回无效 IP 的错误信息列表（供设置页弹窗） |
| `lookupByDoHOnly(hostname)` | **强制走 DoH**，供设置页 DoH 测试使用 |
| `getCDNList(host)` | 供设置页延迟测试列出候选 IP |

---

## 四、仓库层：NetworkRepo / GetchuNetworkRepo

**文件**：`logic/NetworkRepo.kt`（633 行）、`logic/GetchuNetworkRepo.kt`（92 行）

### 4.1 flow 三件套

```kotlin
private fun <T> websiteIOFlow(
    request: suspend () -> Response<ResponseBody>,
    permittedSuccessCode: IntArray? = null,
    action: (String) -> WebsiteState<T>,
) = flow {
    val requestResult = request.invoke()
    val resultBody = requestResult.body()?.string()?.replaceBackupMediaCdnHost()
    val permitted = permittedSuccessCode?.contains(requestResult.code()) == true
    if (permitted || requestResult.isSuccessful) {
        emit(action.invoke(resultBody ?: EMPTY_STRING))
    } else {
        requestResult.throwRequestException()
    }
}.catch { e -> emit(WebsiteState.Error(handleException(e))) }
 .flowOn(Dispatchers.IO)
```

`pageIOFlow` / `videoIOFlow` 结构相同，只是发射的状态类型不同（`PageLoadingState` / `VideoLoadingState`）。`GetchuNetworkRepo` 有一份几乎同构的私有版本，差异是 `bodyToString` 参数（默认 `ResponseBody::string`，Getchu 传 `it.getchuString()` 用 **EUC-JP** 解码）且 `action` 是 `suspend`。

### 4.2 状态模型选用规则（`README_TECH.md` §4）

| 状态类型 | 适用场景 |
|---|---|
| `WebsiteState<T>` | 首页、订阅、账号、修改操作等非分页数据（Loading / Success / Error） |
| `PageLoadingState<T>` | 搜索、收藏、稍后观看、播放列表、在线历史、创作中心（Loading / Success / NoMoreData / Error） |
| `VideoLoadingState<T>` | 视频详情（含"视频不存在""解析失败"语义） |

### 4.3 异常映射：throwRequestException

**文件**：`NetworkRepo.kt:582-608`

```kotlin
internal fun Response<ResponseBody>.throwRequestException(): Nothing {
    val body = errorBody()?.string()
    when (val code = code()) {
        403 -> if (!body.isNullOrBlank()) {
            when {
                "you have been blocked" in body -> throw IPBlockedException(...)
                "Just a moment" in body         -> throw CloudflareBlockedException(...)
                else -> throw HanimeNotFoundException(...)   // 视频被删
            }
        } else throw IllegalStateException("$code ${message()}")

        500 -> throw HanimeNotFoundException(...)
        404 -> if (!isAlreadyLogin) throw IllegalStateException(未登录文案)
               else throw IllegalStateException("$code ${message()}")
        else -> throw IllegalStateException("$code ${message()}")
    }
}
```

### 4.4 handleException

**文件**：`NetworkRepo.kt:610-628`

```kotlin
internal fun handleException(e: Throwable): Throwable = when (e) {
    is CancellationException -> throw e        // ✅ 不吞协程取消
    is ParseException        -> ParseException(解析失败文案)
    is SSLHandshakeException -> SSLHandshakeException(TLS 握手失败文案)
    else -> e
}
```

### 4.5 备用 CDN 替换

**文件**：`Constants.kt:68-80`

```kotlin
const val HANIME_MEDIA_CDN_HOST        = "vdownload.hembed.com"
const val HANIME_BACKUP_MEDIA_CDN_HOST = "1497203185.rsc.cdn77.org"

fun String.replaceBackupMediaCdnHost(): String {
    if (!SettingsRepository.useBackupMediaCdn) return this
    return replace(HANIME_MEDIA_CDN_HOST, HANIME_BACKUP_MEDIA_CDN_HOST, ignoreCase = true)
}
```

在 `websiteIOFlow` / `pageIOFlow` / `videoIOFlow` 三处读取 body 时统一调用 —— 属于「在传输后、解析前做数据修正」。

另有一处同类修正位于 `Parser.kt:52`（AV 站 CDN 节点失效修正）：

```kotlin
// AV 站的 CDN 節點（t26 / t27 / t30 等）可能已失效，需要統一切換成可用的 t33
val avCdnHost = Regex("""^(\s*(?:https?:)?//)t\d+\.cdn2020\.com(?=[/:]|\z)""", RegexOption.IGNORE_CASE)
```

### 4.6 登录流程（特殊：不用 flow 包装器）

`NetworkRepo.login()` 手写 flow，逻辑是「登录 → 再访问 login 页面 → 若返回 404 说明登录成功」：

```kotlin
fun login(email: String, password: String) = flow {
    emit(WebsiteState.Loading)
    val loginPage = HanimeNetwork.hanimeService.getLoginPage()
    val token = loginPage.body()?.string()?.let(Parser::extractTokenFromLoginPage)
    val req = HanimeNetwork.hanimeService.login(token, email, password)
    if (req.isSuccessful) {
        val loginPageAgain = HanimeNetwork.hanimeService.getLoginPage()
        if (loginPageAgain.code() == 404) {
            emit(WebsiteState.Success(req.headers().values("Set-Cookie")))
        } else emit(WebsiteState.Error(账号或密码错误))
    } else emit(WebsiteState.Error(账号或密码错误))
}.catch { e -> emit(WebsiteState.Error(handleException(e))) }
 .flowOn(Dispatchers.IO)
```

---

## 五、Cloudflare 挑战处理

**文件**：`logic/network/interceptor/CloudflareInterceptor.kt`（30 行）、`logic/network/CloudflareVerificationCoordinator.kt`（75 行）

### 5.1 拦截条件

```kotlin
if (response.code == 403 && response.header("cf-mitigated") == "challenge") {
    response.close()
    val verified = CloudflareVerificationCoordinator.verify(context, request.url.toString())
    if (!verified) throw IOException("Cloudflare verification was cancelled, failed, or timed out")
    return chain.proceed(request)   // 重放
}
```

触发条件是 `403` **且** 响应头 `cf-mitigated: challenge`，判定很窄，不会误伤普通 403。

### 5.2 并发验证合并

`CloudflareVerificationCoordinator` 用「host → Verification」映射 + `CountDownLatch` 合并并发请求：

```kotlin
private class Verification {
    val completed = CountDownLatch(1)
    @Volatile var succeeded = false
}
private val verifications = mutableMapOf<String, Verification>()

fun verify(context: Context, url: String): Boolean {
    val host = url.toUri().host?.lowercase() ?: return false
    var shouldLaunch = false
    val verification = synchronized(lock) {
        verifications[host] ?: Verification().also {
            verifications[host] = it
            shouldLaunch = true          // 只有第一个请求负责弹窗
        }
    }
    if (shouldLaunch) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CLOUDFLARE_VERIFICATION)
            .putExtra(EXTRA_CLOUDFLARE_URL, url)
            .putExtra(EXTRA_CLOUDFLARE_HOST, host)
            .addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP))
    }
    val succeeded = verification.completed.await(5, TimeUnit.MINUTES) && verification.succeeded
    if (!succeeded) synchronized(lock) { if (verifications[host] === verification) verifications.remove(host) }
    return succeeded
}
```

设计要点（源码注释原文）：

> *Coalesces simultaneous challenges for the same host and gives every waiting request a definitive result. A cancelled verification must not retry its original request without a clearance cookie.*

即：**同一 host 只弹一次窗**；取消或超时一律返回失败，绝不带脏状态重放原请求。超时常量 `VERIFICATION_TIMEOUT_MINUTES = 5L`。

---

## 六、ECH 加密传输链

### 6.1 装配入口

**文件**：`logic/network/ech/EchHttp.kt`（25 行）

```kotlin
fun OkHttpClient.Builder.echTransport(fallbackDns: Dns = Dns.SYSTEM): OkHttpClient.Builder =
    if (!SettingsRepository.useEch) {
        dns(fallbackDns)
    } else this
        .apply { ConscryptEch.install() }
        .sslSocketFactory(ConscryptEch.socketFactory, ConscryptEch.trustManager)
        .dns(EchDns(fallbackDns))
        .addInterceptor(H3Interceptor())
        .addInterceptor(EchRetryInterceptor())

object EchHttp {
    fun onDohSettingsChanged() = EchDoh.invalidateAll()
}
```

### 6.2 完整执行链

```text
① EchDns 解析
   EchDoh 手写 DNS wire 查询 → 打纯 IP DoH 端点 → 拿 A/AAAA
        │
        ▼
② 取 ECHConfigList
   首选 cloudflare-ech.com 的 HTTPS 记录 → 手解 SVCB key5
   兜底 配置 DoH 的 JSON 查询 → 正则抓 ech= → base64 解码
        │
        ▼
③ 注入并建连
   ConscryptEch.install() → SSLContext("TLSv1.3", ConscryptProvider)
   EchSocketFactory.createSocket → Conscrypt.setEchConfigList(socket, config)
   PolicyTrustManager.getNetworkSecurityPolicy → DomainEncryptionMode
        │
        ▼
④ 静态资源改走 QUIC（H3Interceptor，仅直连）
   HyEchH3.fetchResourceToFile → JNI h3Fetch → Rust quiche
   UDP + ALPN h3 + set_ech_config_list → 落文件 → 包成 Protocol.HTTP_3
        │
        ▼
⑤ 失败回退（EchRetryInterceptor）
   核心域：EchDoh.invalidateEch(host) → 刷新配置后重试
   其他域：ConscryptEch.markEchUnavailable(host) → 明文重试
```

### 6.3 EchDoh：DoH 与 ECHConfigList 获取

**文件**：`logic/network/ech/EchDoh.kt`（394 行，本模块最核心文件）

#### 常量与缓存

```kotlin
private const val LIVE_SOURCE_HOST  = "cloudflare-ech.com"
private const val CLOUDFLARE_ASN    = 13335
private const val FAIL_COOLDOWN_MS  = 30_000L
private const val MIN_TTL_MS        = 60_000L
private const val MAX_TTL_MS        = 60 * 60 * 1000L
private const val ECH_CACHE_MIN_MS  = 60 * 60 * 1000L
private const val ECH_CACHE_MAX_MS  = 5 * 60 * 60 * 1000L
private const val DNS_CACHE_TTL_MS  = 5 * 60 * 1000L
private const val SINGLE_QUERY_TIMEOUT_MS = 2_500L

private val pureDohIps = listOf(
    "223.5.5.5", "223.6.6.6", "1.12.12.12", "120.53.53.53", "101.198.193.29", "101.198.192.33",
)

// 七张缓存表
private val resolverCache, dnsCache, asnCache, cfHostCache, echCache, echFailed, ownFirst
```

#### 端点枚举与降级

```kotlin
private fun endpoints(): List<DohEndpoint> {
    val configured = configuredEndpoint()                      // 用户配置的 DoH
    val pure = pureDohIps.map { DohEndpoint("https://$it/dns-query", emptyList()) }  // 纯 IP 端点
    return (listOfNotNull(configured) + pure).distinctBy { it.url }
}
```

`configuredEndpoint()` 在 `useDoH == false` 时返回 `null`，此时只剩纯 IP 端点 —— 即 **ECH 链可以完全脱离用户的 DoH 设置独立工作**。

#### 手写 DNS 报文

```kotlin
private fun buildQuery(name: String, type: Int): ByteArray {
    val output = ByteArrayOutputStream()
    output.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))  // ID/FLAGS/QD=1
    name.split('.').forEach { label ->
        output.write(label.length)
        output.write(label.toByteArray(Charsets.US_ASCII))
    }
    output.write(0)                       // 根标签
    output.write((type ushr 8) and 0xFF)
    output.write(type and 0xFF)           // QTYPE
    output.write(0); output.write(1)      // QCLASS = IN
    return output.toByteArray()
}
```

以 `Accept: application/dns-message` 发包，`Base64.NO_WRAP or Base64.URL_SAFE` + `trimEnd('=')` 编码到 URL 查询串。**完全绕开系统 DNS 与 DoH 库**，只走 OkHttp 的传输层。

#### 手解 SVCB / HTTPS 记录

```kotlin
private const val TYPE_TXT = 16
private const val TYPE_HTTPS = 65
private const val SVCB_KEY_ECH = 5

internal fun parseSvcbEch(message: ByteArray): Pair<ByteArray, Long>? {
    // 自行走 TLV：跳过 name（含 0xC0 压缩指针）、读 TYPE/TTL/RDLENGTH
    // 命中 type==65 时，跳过优先级(SvcPriority) + TargetName，
    // 遍历 SvcParam，取 key == SVCB_KEY_ECH 的 value 作为 ECHConfigList
}
```

`skipName()` 处理了 DNS 名称压缩指针（`(length and 0xC0) == 0xC0` → 跳 2 字节）。

#### 双源获取 + 顺序翻转

```kotlin
val first = if (host != LIVE_SOURCE_HOST && !ownFirst.contains(host)) LIVE_SOURCE_HOST else host
val second = if (first == host) LIVE_SOURCE_HOST else host

val hit = if (first == LIVE_SOURCE_HOST) {
    fetchLiveEch() ?: fetchEchJson(host)
} else {
    fetchEchJson(first) ?: fetchLiveEch() ?: fetchEchJson(second)
}
```

- `fetchLiveEch()`：`pureDohIps.shuffled()` 逐个发 wire 查询查 `cloudflare-ech.com` 的 HTTPS 记录
- `fetchEchJson()`：对配置的 DoH 端点发 `Accept: application/dns-json`，用 `Regex("""ech=([A-Za-z0-9+/=_-]+)""")` 抓值，`Regex(""""TTL"\s*:\s*(\d+)""")` 取最小 TTL
- `ownFirst` 是「该 host 已主动刷新过」的标记集合，由 `invalidateEch()` 写入，使后续查询优先查该 host 自己的记录

TTL 处理：`fetchLiveEch` 用 `coerceIn(ECH_CACHE_MIN_MS, ECH_CACHE_MAX_MS - 1) + 1`，`fetchEchJson` 用 `coerceIn(MIN_TTL_MS, MAX_TTL_MS)`。

#### ASN 真伪校验

```kotlin
private fun isCloudflareIp(address: InetAddress): Boolean {
    if (address.address.size != 4) return false
    val ip = address.hostAddress ?: return true
    asnCache[ip]?.let { return it }
    val queryName = ip.split('.').reversed().joinToString(".") + ".origin.asn.cymru.com"
    val txt = runCatching { queryPureWire(queryName, TYPE_TXT) }.getOrNull()
    val asn = txt?.let { Regex(""""?(\d{2,6})\s*\|""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    // Unknown ASN is treated as Cloudflare rather than risking a protected host.
    val cloudflare = asn == null || asn == CLOUDFLARE_ASN
    if (asn != null) asnCache[ip] = cloudflare
    return cloudflare
}
```

查 Team Cymru 的 `origin.asn.cymru.com` TXT 记录；**查不到时保守判定为 Cloudflare**（宁可多试 ECH，也不冒明文暴露风险）。IPv6 直接返回 false。

#### 失效控制

```kotlin
fun invalidateEch(host: String) {
    echCache.remove(host); echFailed.remove(host)
    echCache.remove(LIVE_SOURCE_HOST)
    if (host != LIVE_SOURCE_HOST) ownFirst.add(host)
}

fun invalidateAll() { /* 清空全部 7 张表 */ }
```

### 6.4 EchDns：解析器

```kotlin
class EchDns(private val fallback: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = runCatching { EchDoh.resolve(hostname) }.getOrNull()
        if (!addresses.isNullOrEmpty()) return addresses
        if (EchTransportPolicy.shouldFailClosed(hostname)) {
            throw UnknownHostException("DoH resolution failed for protected host $hostname")
        }
        return fallback.lookup(hostname)
    }
}
```

**所有 host 都先走 DoH**，核心域解析失败则 fail-closed。

### 6.5 ConscryptEch：TLS 注入与隐藏契约

**文件**：`logic/network/ech/ConscryptEch.kt`（215 行）

#### ⚠️ 关键契约（源码注释原文）

> *Conscrypt locates the network policy through reflection on `PolicyTrustManager.getNetworkSecurityPolicy`. Keep that method public and keep the class from being renamed by R8, otherwise ECH is silently disabled.*

```kotlin
class PolicyTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
    // ... 委托系统 TrustManager
    @Suppress("unused")
    fun getNetworkSecurityPolicy(): NetworkSecurityPolicy = POLICY
}

private val POLICY = object : NetworkSecurityPolicy {
    override fun isCertificateTransparencyVerificationRequired(hostname: String?) = false
    override fun getCertificateTransparencyVerificationReason(hostname: String?) =
        CertificateTransparencyVerificationReason.UNKNOWN
    override fun getDomainEncryptionMode(hostname: String?): DomainEncryptionMode =
        if (hostname != null && EchHosts.shouldTryEch(hostname)) DomainEncryptionMode.ENABLED
        else DomainEncryptionMode.DISABLED
}
```

**R8 一旦重命名该方法，`install()` 仍返回 `true`，但 ECH 静默失效**。对应的 ProGuard 规则（`proguard-rules.pro:37-39`）：

```proguard
-keep class io.github.daisukikaffuchino.han1meviewer.logic.network.ech.ConscryptEch$PolicyTrustManager { *; }
```

#### 双通道注入

项目同时用两条路让 ECH 生效：

1. **Conscrypt 自主路径**：`getDomainEncryptionMode()` 返回 `ENABLED`，Conscrypt 内部自行取 ECH 配置
2. **手动注入路径**：`EchSocketFactory.createSocket()` 显式调 `Conscrypt.setEchConfigList(socket, config)`

```kotlin
private class EchSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
    private fun prepare(socket: Socket, host: String?): Socket {
        if (host == null || socket !is SSLSocket || !EchHosts.shouldTryEch(host)) return socket

        val core = EchHosts.isCoreDomain(host)
        val failClosed = EchTransportPolicy.shouldFailClosed(host)

        if (echUnavailable.contains(host.lowercase())) {
            if (failClosed) throw IOException("ECH is unavailable for protected host $host")
            return socket
        }

        val config = EchDoh.echConfigList(host)
        if (config == null) {
            if (failClosed) throw IOException("Unable to obtain an ECHConfigList for protected host $host")
            markEchUnavailable(host); return socket
        }

        return try {
            Conscrypt.setEchConfigList(socket, config)
            socket
        } catch (throwable: Throwable) {
            if (failClosed) throw IOException("Failed to inject ECHConfigList for $host", throwable)
            markEchUnavailable(host); socket
        }
    }
    // 三个 createSocket(host, port, ...) 重载都过 prepare；InetAddress 重载不过（无 SNI 场景）
}
```

注意：`createSocket(host: InetAddress, port: Int)` 与四参版本**不过 `prepare`** —— 因为没有目标主机名，无从注入。

#### SSLContext 与信任链

```kotlin
private val provider: java.security.Provider by lazy { Conscrypt.newProvider() }

private val systemTrustManager: X509TrustManager by lazy {
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        ?: throw IllegalStateException("System X509TrustManager is unavailable")
}

private val sslContext: SSLContext by lazy {
    SSLContext.getInstance("TLSv1.3", provider)
        .apply { init(null, arrayOf<TrustManager>(trustManager), SecureRandom()) }
}
```

**只支持 TLS 1.3**（ECH 的硬性前提）。

#### 不可用域黑名单

```kotlin
private val echUnavailable: MutableSet<String> = ConcurrentHashMap.newKeySet()
fun markEchUnavailable(host: String) { if (echUnavailable.add(host.lowercase())) {...} }
fun echUnavailableHosts(): List<String> = echUnavailable.sorted()
```

#### 重试拦截器

```kotlin
class EchRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        return try {
            chain.proceed(request)
        } catch (throwable: Throwable) {
            if (!isEchRejected(throwable)) throw throwable
            if (EchTransportPolicy.shouldFailClosed(host)) {
                EchDoh.invalidateEch(host)          // 核心域：刷新配置后重试
            } else {
                ConscryptEch.markEchUnavailable(host) // 其他域：降级明文
            }
            chain.proceed(request)
        }
    }

    private fun isEchRejected(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }.any { cause ->
            cause.javaClass.simpleName.contains("EchRejected", ignoreCase = true) ||
                cause.message?.contains("ECH_REJECTED", ignoreCase = true) == true
        }
}
```

判定走**异常链遍历**（`generateSequence`），同时匹配类名与消息，兼容不同 Conscrypt 版本。

### 6.6 域名与策略

**文件**：`logic/network/ech/EchHosts.kt`（22 行）、`logic/network/ech/EchTransportPolicy.kt`（25 行）

```kotlin
object EchHosts {
    fun shouldTryEch(@Suppress("UNUSED_PARAMETER") host: String): Boolean = true   // 恒 true
    fun isCoreDomain(host: String): Boolean {
        val normalized = host.lowercase()
        return HANIME_HOSTNAME.any { domain -> normalized == domain || normalized.endsWith(".$domain") }
    }
    fun isProtected(host: String): Boolean = shouldTryEch(host)
}
```

```kotlin
object EchTransportPolicy {
    fun isProxyRoute(proxyType: Int) = proxyType != ProxyType.Direct.id
    fun shouldFailClosed(host: String, proxyType: Int) = EchHosts.isCoreDomain(host) && !isProxyRoute(proxyType)
    fun shouldUseH3(proxyType: Int) = !isProxyRoute(proxyType)
}
```

**fail-closed 策略矩阵**：

| 场景 | 核心域（`hanime1.me` / `.com` / `hanimeone.me` / `javchu.com` 及子域） | 其他域 |
|---|---|---|
| 直连 + ECH 不可用 | **抛 IOException，拒绝降级明文** | 标记不可用，明文继续 |
| 走代理（HTTP/SOCKS/System） | 明文继续（**代理优先于 ECH**） | 明文继续 |
| QUIC/H3 | 直连时启用 | 代理时禁用 |

设计注释原文：

> *ECH is opt-in. When the user selects a proxy route, the proxy must win over direct-only ECH behavior so proxy-only networks remain usable.*

### 6.7 native-h3：Rust quiche

**文件**：`native-h3/src/lib.rs`（329 行）、`native-h3/Cargo.toml`

#### 依赖与产物

```toml
[package] name = "hn1_h3"
[lib]    name = "hn1_h3"
```

vendor 了 **quiche 0.22**（由 `tools/prepare_quiche.py` 下载 / 校验 / 解压 / 打补丁，标记文件 `native-h3/vendor/.patched`）。

#### QUIC 配置

```rust
let mut config = quiche::Config::new(quiche::PROTOCOL_VERSION)?;
config.verify_peer(true);
config.load_verify_locations_from_file(ca_path)?;
config.set_application_protos(&[b"h3"])?;
config.set_max_idle_timeout(30_000);
config.set_max_recv_udp_payload_size(1350);
config.set_initial_max_data(10_000_000);
config.set_initial_max_stream_data_bidi_local(1_000_000);
config.set_initial_max_stream_data_bidi_remote(1_000_000);
config.set_initial_max_stream_data_uni(1_000_000);
config.set_initial_max_streams_bidi(100);
config.set_initial_max_streams_uni(100);
config.set_disable_active_migration(true);
config.set_cc_algorithm(quiche::CongestionControlAlgorithm::CUBIC);
if let Some(value) = ech { config.set_ech_config_list(value); }
```

#### 时序

| 阶段 | 超时 |
|---|---|
| QUIC 握手 | 8 秒（`handshake_started.elapsed() >= Duration::from_secs(8)`） |
| H3 请求 | 25 秒（`request_started.elapsed() >= Duration::from_secs(25)`） |
| `set_read_timeout` | `remaining.min(500ms)`，轮询式 `on_timeout()` 驱动 |

请求只有一次（`h3.send_request(&mut connection, &headers, true)`），请求头固定为 `:method=GET` / `:scheme=https` / `:authority=<host>` / `:path` / `user-agent` / `accept: image/avif,image/webp,*/*`（可选 `referer`）。

#### 结果协议

返回 JSON 字符串：

```json
{"ok":true,"status":200,"body_len":12345,"saved_to":"/path/to/file","error":""}
```

失败分支全部走同一个 `result_json(false, ...)`，错误信息为可读文本（`"QUIC handshake timed out"` / `"Peer error: ..."` / `"HTTP status 403"` 等）。成功条件是 `status == 200 && !body.is_empty() && write(output_file) ok`。

#### JNI 入口

```rust
#[no_mangle]
pub extern "system" fn Java_io_github_daisukikaffuchino_han1meviewer_logic_network_ech_HyEchH3_h3Fetch(
    mut env: JNIEnv, _class: JClass,
    host: JString, peer_ip: JString, ech_base64: JString, path: JString,
    referer: JString, ca_path: JString, output_file: JString, user_agent: JString,
) -> jstring
```

`ca_path` 为空时兜底 `"/system/etc/security/cacerts"`；`path` 为空时兜底 `"/"`。自带一个手写 `base64_decode()`（不引第三方 crate）。

### 6.8 Android 侧包装：HyEchH3

**文件**：`logic/network/ech/HyEchH3.kt`（210 行）

#### 静态资源判定

```kotlin
private val staticExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "ico", "svg",
    "css", "js", "mjs", "woff", "woff2", "ttf",
)
```

配套 `mimeFor(url)` 做扩展名 → MIME 映射。

#### H3 失败冷却（SharedPreferences）

```kotlin
private const val PREFS_NAME = "ech_h3_state"
private const val FAIL_TTL_MS = 24 * 60 * 60 * 1000L   // 失败冷却 24 小时

fun shouldTryH3(host: String): Boolean {
    val retryAt = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong("bad:$host", 0L)
    return System.currentTimeMillis() >= retryAt
}

private fun rememberH3(context: Context, host: String, success: Boolean) {
    val retryAt = if (success) 0L else System.currentTimeMillis() + FAIL_TTL_MS
    ...putLong("bad:$host", retryAt)
}
```

#### 主流程

```kotlin
fun fetchResourceToFile(url: String, rememberResult: Boolean = true, respectTransportPolicy: Boolean = true): File? {
    if (respectTransportPolicy && !EchTransportPolicy.shouldUseH3()) return null
    if (!shouldTryH3(host)) return null

    val ip = EchDoh.resolve(host).firstOrNull()?.hostAddress ?: return null
    val ech = EchDoh.echConfigList(host)     // 可能为 null

    // 核心域必须带 ECH，否则拒绝明文 QUIC
    if (EchHosts.isCoreDomain(host) && (ech == null || ech.isEmpty())) {
        EchLog.w(TAG, "Refusing plaintext QUIC for protected host=$host")
        return null
    }

    val output = File(context.cacheDir, "h3-${System.nanoTime()}.$extension")
    val saved = fetchToFile(context, host, ip, ech, path, output)
    ...
}
```

#### CA bundle 导出

Rust 侧需要 PEM 文件，Android 侧从 `AndroidCAStore` 导出：

```kotlin
private fun caBundlePath(context: Context): String {
    val output = File(context.cacheDir, "han1me-system-ca.pem")
    if (output.exists() && output.length() > 1024L) return output.absolutePath   // 已缓存
    val keyStore = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
    // 遍历别名 → X509Certificate → PEM 拼接 → 写文件
}
```

#### 原生库加载

```kotlin
private const val LIB_NAME = "chino"
private fun ensureLoaded(): Boolean = runCatching { System.loadLibrary(LIB_NAME); true }
    .getOrElse { EchLog.w(TAG, "H3 native library is unavailable: ${it.message}"); false }
```

注意：库名是 **`chino`**，不是 `hn1_h3` —— Rust 静态库被链接进 `libchino.so`。

### 6.9 H3Interceptor

**文件**：`logic/network/ech/H3Interceptor.kt`（64 行）

```kotlin
override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request(); val url = request.url
    if (!EchTransportPolicy.shouldUseH3())            return chain.proceed(request)
    if (request.method != "GET" || !HyEchH3.isStaticAsset(url)) return chain.proceed(request)
    if (!HyEchH3.shouldTryH3(url.host))               return chain.proceed(request)

    val file = runCatching { HyEchH3.fetchResourceToFile(url.toString()) }.getOrNull()
    if (file == null || !file.exists() || file.length() == 0L) return chain.proceed(request)

    return Response.Builder()
        .request(request).protocol(Protocol.HTTP_3).code(200).message("OK (H3+ECH)")
        .body(FileBody(file, HyEchH3.mimeFor(url).toMediaTypeOrNull()))
        .build()
}

private class FileBody(private val file: File, private val mediaType: MediaType?) : ResponseBody() {
    override fun source(): BufferedSource = file.source().buffer()
    override fun close() { super.close(); file.delete() }   // 读完即删
}
```

**三道闸门依次放行**：传输策略（直连）→ 请求特征（GET + 静态扩展名）→ 主机可用性（24h 冷却）。任一不过就走普通 TCP+ECH。文件在 `close()` 时删除。

### 6.10 构建链

**文件**：`buildSrc/src/main/java/EchH3.kt`（223 行）、`app/src/main/cpp/CMakeLists.txt`、`tools/prepare_quiche.py`、`tools/verify_ech.py`

```kotlin
object EchH3 {
    const val NDK_VERSION = "28.2.13676358"
    const val CMAKE_VERSION = "3.22.1"
    const val TARGET_ABI = "arm64-v8a"

    private const val JNI_SYMBOL =
        "Java_io_github_daisukikaffuchino_han1meviewer_logic_network_ech_HyEchH3_h3Fetch"
}
```

CMake 参数（`cmakeArguments`）：

```kotlin
"-DANDROID_STL=c++_shared",
"-DHN1_H3_STATIC_LIB=<root>/native-h3/target/aarch64-linux-android/release/libhn1_h3.a",
"-DHN1_H3_VERSION_SCRIPT=<app>/src/main/cpp/chino.exports",
```

Gradle 任务链：

| 任务 | 作用 |
|---|---|
| `prepareEchH3` | 调 `tools/prepare_quiche.py`，下载/校验/解压/打补丁 quiche，产出标记 `vendor/.patched` |
| `buildEchH3Arm64` | `cargo ndk -t arm64-v8a build --release --lib --locked` |
| `verifyEchH3Archive` | 用 NDK 的 `llvm-nm -g` 检查静态库导出 JNI 符号 |
| `verifyEchBridgeJs` | `tools/verify_ech.py bridge`，检查 JS 语法与路由 |
| `verifyEchApk` | 构建 debug APK 后 `tools/verify_ech.py apk`，校验合并后的 .so |

挂载方式（`EchH3.kt:122-131`）：

```kotlin
tasks.matching {
    it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") || it.name == "preBuild"
}.configureEach { dependsOn(verifyEchH3Archive) }

tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyEchBridgeJs) }
```

**Windows 特化**（`configureWindowsToolchain`）：必须装 `stable-x86_64-pc-windows-gnu` 工具链，并把 NDK 的 `llvm-dlltool.exe` / `llvm-ar.exe` 复制成 `dlltool.exe` / `ar.exe` 放进 `build/ech-h3-host-tools`，再注入 `PATH` + `CMAKE` / `CMAKE_GENERATOR=Ninja` / `CMAKE_MAKE_PROGRAM=ninja.exe`。

`CMakeLists.txt` 用 `--whole-archive` 链入静态库，再用 version script 收口导出面：

```cmake
if (NOT DEFINED HN1_H3_STATIC_LIB)
    message(FATAL_ERROR "HN1_H3_STATIC_LIB must point to libhn1_h3.a")
endif ()
target_link_libraries(chino PRIVATE ${log-lib} c++_shared
    -Wl,--whole-archive ${HN1_H3_STATIC_LIB} -Wl,--no-whole-archive)
target_link_options(chino PRIVATE "-Wl,--version-script=${HN1_H3_VERSION_SCRIPT}")
```

`chino.exports` 只导出两个符号：`HyEchH3.h3Fetch` 与 video 页的签名校验函数。

### 6.11 诊断工具

**文件**：`logic/network/ech/EchDiagnostics.kt`（135 行）、`logic/network/ech/EchLog.kt`、`ui/screen/settings/EchTestScreen.kt`

`EchDiagnostics.run(host)` 依次执行四步并汇总成 `EchDiagnosticReport`：

```kotlin
data class EchDiagnosticReport(
    val host: String, val resolvedAddresses: List<String>, val echConfigBytes: Int,
    val httpStatus: Int, val protocol: String, val elapsedMillis: Long,
    val h3Result: String, val error: String? = null,
) {
    val successful get() = error == null && resolvedAddresses.isNotEmpty()
        && echConfigBytes > 0 && httpStatus > 0
}
```

| 步骤 | 动作 |
|---|---|
| 1 | `EchDoh.resolve(host)` 并打印解析到的地址 |
| 2 | `EchDoh.echConfigList(host)` 并打印字节数 |
| 3 | 用 `ServiceCreator.hClient` 请求 `https://$host/`，记录状态码 / 协议 / 耗时 |
| 4 | `HyEchH3.fetchResourceToFile("https://$host/favicon.ico", rememberResult=false, respectTransportPolicy=false)` 独立验 H3 |

`EchLog` 是内存环形缓冲（d/i/w/e + clear），`EchTestScreen` 直接读它展示。

---

## 七、WebView 三层接管

WebView 是最难处理的环节 —— `shouldInterceptRequest` **拿不到 POST body**。项目用三层兜底。

**文件**：`logic/network/ech/HyWebViewHelper.kt`（213 行）、`EchWebBridge.kt`（260 行）、`EchWebBridgeJs.kt`（310 行）

### 7.1 三层结构

| 层 | 位置 | 覆盖 |
|---|---|---|
| ① 响应拦截 | `HyWebViewHelper.intercept(WebResourceRequest)` | GET（含 H3 静态资源） |
| ② JS Hook | `EchWebBridgeJs.script()` 注入 | `fetch` / `XMLHttpRequest` / `form submit` |
| ③ 原生桥 | `EchWebBridge.send()` `@JavascriptInterface` | 带 body 的任意方法 |

### 7.2 总开关

```kotlin
private fun shouldUseEchWebView(): Boolean =
    SettingsRepository.useEch && !EchTransportPolicy.isProxyRoute()
```

**走代理时整个 WebView ECH 接管都不启用**（代理优先原则）。

### 7.3 第一层：shouldInterceptRequest

```kotlin
fun intercept(request: WebResourceRequest): WebResourceResponse? {
    if (!shouldUseEchWebView()) return null
    val host = request.url.host ?: return null
    val method = (request.method ?: "GET").uppercase()

    if (!EchHosts.isCoreDomain(host)) return null      // 不干涉无关站点
    if (method != "GET") return null                    // POST 交给 JS 桥

    // 静态资源优先走 H3
    if (HyEchH3.isStaticAsset(url.toHttpUrl())) {
        val cacheFile = runCatching { HyEchH3.fetchResourceToFile(url) }.getOrNull()
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            return WebResourceResponse(HyEchH3.mimeFor(url.toHttpUrl()), null, 200, "OK",
                emptyMap(), DeletingFileInputStream(cacheFile))
        }
    }

    if (!ConscryptEch.ready && !ConscryptEch.install()) {
        return if (EchTransportPolicy.shouldFailClosed(host)) failClosed(host, "ECH transport is not ready") else null
    }

    var lastError = "unknown error"
    repeat(2) { attempt ->          // 最多两次（第二次前刷新 ECH 配置）
        try {
            val builder = Request.Builder().url(url).get()
            request.requestHeaders.forEach { (key, value) ->
                if (key.equals("Host", true) || key.equals("Content-Length", true) || key.equals("Cookie", true)) return@forEach
                runCatching { builder.header(key, value) }
            }
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)

            client.newCall(builder.build()).execute().use { response ->
                val body = response.body.bytes()
                val (mime, charset) = parseContentType(response.header("Content-Type") ?: "text/html")
                syncCookies(url, response)
                return WebResourceResponse(mime, charset, response.code,
                    response.message.ifBlank { "OK" }, headers.toMap(), ByteArrayInputStream(body))
            }
        } catch (throwable: Throwable) {
            lastError = throwable.message ?: throwable.javaClass.simpleName
            if (attempt == 0) { EchDoh.invalidateEch(host); runCatching { Thread.sleep(300) } }
        }
    }
    return if (EchTransportPolicy.shouldFailClosed(host)) failClosed(host, lastError) else null
}
```

细节：**主动剔除 `Host` / `Content-Length` / `Cookie` 三个头**（前两个由 OkHttp 管理，Cookie 从 `CookieManager` 单独取），并逐个 `runCatching` 容错非法头。

### 7.4 第二层：JS Hook

`EchWebBridgeJs.TEMPLATE` 是一段 300 行 IIFE，通过 `__ECH_PROTECTED__` 占位符注入受保护域名数组。它做四件事：

| Hook | 行为 |
|---|---|
| `window.fetch` | 非 GET/HEAD 且受保护 → 序列化 body → `nativeSend()` |
| `XMLHttpRequest.prototype.open/setRequestHeader/send/abort` | 记录状态到 `this.__echRequest`，`send` 时改写；`fakeXhrResponse()` 伪造 `readyState`/`status`/`responseText`/`response`/`getAllResponseHeaders` 并派发 `readystatechange`/`load`/`loadend` |
| `document.addEventListener('submit', ..., true)` | 拦截**含密码框且无文件框**的表单（捕获阶段），`preventDefault` 后走 `EchBridge.postForm()` |
| `JSON` 序列化 | `serializeBody()` 支持 `string` / `URLSearchParams` / `FormData`（无文件时）/ `ArrayBuffer` / `TypedArray`；**含文件的 FormData 返回 `null`，保持原生行为** |

30 秒超时保护：

```javascript
var timeout = setTimeout(function () {
  if (pending[id]) { delete pending[id]; resolve({ ok: false, status: 502, error: 'ECH bridge timeout' }); }
}, 30000);
```

### 7.5 第三层：原生桥

```kotlin
class EchWebBridge(private val webView: WebView, private val onFormLoginSuccess: ((String) -> Unit)? = null) {

    @JavascriptInterface
    fun send(id: String, method: String, url: String, headersJson: String, bodyBase64: String, pageUrl: String) {
        if (!isProtectedUrl(url)) { resolve(id, errorPayload("Bridge rejected a non-protected URL")); return }
        Thread {
            val payload = runCatching { client.newCall(buildRequest(...)).execute().use { response -> /* JSON */ } }
                .getOrElse { errorPayload(it.message ?: it.javaClass.simpleName) }
            resolve(id, payload)
        }.start()
    }

    @JavascriptInterface
    fun postForm(url: String, bodyBase64: String, pageUrl: String) { /* 表单登录专用，不跟随重定向 */ }

    @JavascriptInterface
    fun log(message: String) { EchLog.i(TAG, "[js] ${message.take(300)}") }
}
```

`isProtectedUrl()` 是**安全边界**：非受保护域名直接拒绝，不让 JS 把原生栈当通用代理用。

回调靠 `webView.evaluateJavascript`：

```kotlin
private fun resolve(id: String, payload: JSONObject) {
    val script = "window.__echBridgeResolve(${JSONObject.quote(id)}, ${JSONObject.quote(payload.toString())});"
    webView.post { runCatching { webView.evaluateJavascript(script, null) } }
}
```

### 7.6 两个 client 与 Cookie 同步

| client | `followRedirects` | 用途 |
|---|---|---|
| `client` | `true` | 普通 fetch / XHR |
| `formClient` | **`false`** | 表单登录 —— 需要读 `Location` 头判断成败 |

两者都装 `cookieSync()` 网络拦截器做双向同步：

```kotlin
private fun cookieSync(client: OkHttpClient.Builder) = client.addNetworkInterceptor { chain ->
    val request = chain.request()
    val webViewCookie = CookieManager.getInstance().getCookie(request.url.toString())
    val requestWithCookie = if (webViewCookie.isNullOrBlank()) request
        else request.newBuilder().header("Cookie", webViewCookie).build()

    val response = chain.proceed(requestWithCookie)
    val cookieManager = CookieManager.getInstance()
    response.headers.values("Set-Cookie").forEach { raw ->
        var cookie = raw.replace(Regex(""";\s*Domain=[^;]+""", IGNORE_CASE), "")   // 剥 Domain
        cookie = cookie.replace(Regex(""";\s*Secure""", IGNORE_CASE), "")          // 剥 Secure
        cookie = cookie.replace(Regex(""";\s*SameSite=[^;]+""", IGNORE_CASE), "; SameSite=Lax")  // 改 SameSite
        runCatching { cookieManager.setCookie(request.url.toString(), cookie) }
    }
    runCatching { cookieManager.flush() }
    response
}
```

### 7.7 fail-closed 页面

```kotlin
private fun failClosed(host: String, reason: String): WebResourceResponse {
    val safeReason = reason.replace("<", "&lt;")     // 最小 XSS 防护
    val page = """...<h3>连接失败</h3><p>无法安全地连接到 $host，已阻止本次访问。</p>
                  <p>原因：$safeReason</p><p>可到「设置 - 网络」检查 DoH 配置后重试。</p>..."""
    return WebResourceResponse("text/html", "utf-8", 502, "Bad Gateway",
        mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(page.toByteArray(UTF_8)))
}
```

返回 **502 + `Cache-Control: no-store`**，而不是让 WebView 裸失败。

### 7.8 注入时机

```kotlin
fun installWebView(webView: WebView, onFormLoginSuccess: ((String) -> Unit)? = null): EchWebBridge {
    val bridge = EchWebBridge(webView, onFormLoginSuccess)
    if (!shouldUseEchWebView()) return bridge
    webView.addJavascriptInterface(bridge, EchWebBridge.NAME)   // NAME = "EchBridge"
    return bridge
}

fun injectBridge(webView: WebView, url: String?) {
    if (!shouldUseEchWebView()) return
    val host = URI(url).host ?: return
    if (!EchHosts.isCoreDomain(host)) return
    webView.evaluateJavascript(EchWebBridgeJs.script(HANIME_HOSTNAME), null)
}
```

JS 脚本自带幂等保护：`if (window.__echBridgeInstalled || !window.EchBridge) return;`。

---

## 八、其他链路的网络接入

### 8.1 图片：Coil 3

**文件**：`HanimeApplication.kt:45-65`

```kotlin
override fun newImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .apply { if (SettingsRepository.useEch) echTransport(HDns()) else dns(HDns()) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .build()
}
```

**图片链路的 client 是独立构造的**（不复用 `ServiceCreator.hClient`，因此也没有 `HCookieJar` / Cloudflare 拦截），但共享同一套 `echTransport(HDns())`。设置变更后靠 `SingletonImageLoader.reset()` 重建。

### 8.2 下载：WorkManager + 断点续传

**文件**：`worker/HanimeDownloadWorker.kt`（661 行）、`worker/HanimeDownloadManager.kt`、`worker/WorkerMixin.kt`

| 项 | 实现 |
|---|---|
| 取长度 | 先 `HEAD`，失败则 `Range: bytes=0-0` 读 `Content-Range` |
| 续传 | `RandomAccessFile.seek(downloadedLength)` 或 SAF `FileChannel.position(...)`；请求带 `Range: bytes=N-`，**校验 `response.code == 206`** |
| 进度落库 | `RESPONSE_INTERVAL = 500L` 节流，同时更新通知与 WorkManager 进度 |
| 流中断重试 | `"stream was reset: CANCEL"` → 最多 `MAX_STREAM_RETRY_COUNT = 3` 次 |
| 任务重试 | `MAX_WORK_RETRY_COUNT = 3`，`BackoffPolicy.LINEAR` + `BACKOFF_DELAY = 10_000L` |
| 约束 | `NetworkType.CONNECTED` + `setRequiresStorageNotLow(true)` |
| 前台服务 | `ForegroundInfo(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` |
| 存储 | SAF 优先（`SafFileManager.getDownloadVideoFileUri`），否则 `RandomAccessFile(file, "rwd")` |

**下载不可重试的判定**在 `isRetryableNetworkError()`：只认 `UnknownHostException` / `SocketTimeoutException` / `ConnectException` / `SocketException` / 非 Canceled 的 `IOException`；`"Canceled"` 消息被显式排除，避免把用户主动暂停当网络故障重试。

整个下载链路**不经过 `CloudflareInterceptor`，也不经过 `HCookieJar`** —— 媒体 CDN 一般不需要。

### 8.3 播放器

| 引擎 | 文件 | 网络配置 |
|---|---|---|
| ExoPlayer | `ui/player/ExoPlaybackEngine.kt:190-198` | `DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT).setDefaultRequestProperties(request.headers)`，再交给 `DefaultDataSource.Factory` |
| MPV | `ui/player/MpvPlaybackEngine.kt:308-340` | 直接配 libmpv：`user-agent`、`tls-ca-file`、`tls-verify`、`network-timeout`、`cache`/`cache-secs`，**HTTP 代理走 `http-proxy`**；本地文件走 `fd://<detachedFd>` |
| 系统 | `ui/player/SystemPlaybackEngine.kt:63` | `setDataSource(context, request.uri.toUri(), request.headers)` |

画质与请求头由 `HanimeResolution` 解析后随 `PlaybackRequest.headers` 传入（`PlaybackEngine.kt:60`）。

**注意**：MPV 不走 OkHttp，所以 `useEch` 对它无效；它只认自己的代理设置，且**只支持 `TYPE_HTTP`**（SOCKS 弹警告 `mpv_socks5_warning`）。

### 8.4 代理：HProxySelector

**文件**：`logic/network/HProxySelector.kt`（98 行，参考 EhViewer_CN_SXJ）

```kotlin
const val TYPE_DIRECT = 0; const val TYPE_SYSTEM = 1
const val TYPE_HTTP = 2;   const val TYPE_SOCKS  = 3

override fun select(uri: URI?): MutableList<Proxy> {
    val type = SettingsRepository.proxyType
    if (type == TYPE_HTTP || type == TYPE_SOCKS) {
        val ip = SettingsRepository.proxyIp; val port = SettingsRepository.proxyPort
        if (ip.isNotBlank() && port != -1) {
            return mutableListOf(Proxy(if (type == TYPE_HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS,
                InetSocketAddress(InetAddress.getByName(ip), port)))
        }
    }
    return delegation?.select(uri) ?: alternative.select(uri)
}
```

委托链设计：`delegation`（`TYPE_DIRECT → NullProxySelector` / `TYPE_SYSTEM → 系统默认` / HTTP/SOCKS → `null`），`alternative` 兜底。

**WebView 全局代理补丁**（`#issue-39` 注释）：

```kotlin
fun rebuildNetwork() {
    val properties = System.getProperties()
    when (SettingsRepository.proxyType) {
        TYPE_HTTP, TYPE_SOCKS -> {
            properties["proxySet"] = true.toString()
            properties["proxyHost"] = SettingsRepository.proxyIp
            properties["proxyPort"] = SettingsRepository.proxyPort.toString()
        }
        else -> { properties["proxySet"] = false.toString(); properties["proxyHost"] = ""; properties["proxyPort"] = "" }
    }
}
```

在 `HanimeApplication.onCreate()` 里同时调 `ProxySelector.setDefault(HProxySelector())` 与 `HProxySelector.rebuildNetwork()`。

### 8.5 Cookie：HCookieJar

**文件**：`logic/network/HCookieJar.kt`（48 行）、`util/Cookies.kt`（58 行）

```kotlin
class HCookieJar : CookieJar {
    companion object {
        @JvmStatic val cookieMap: MutableMap<String, MutableList<Cookie>> = mutableMapOf()   // ⚠️ 全局静态
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        cookieMap[host]?.let { cookies.addAll(it) }
        // 每次请求都注入持久化的登录 Cookie
        cookies.addAll(CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(host))
        if (SettingsRepository.cloudFlareCookieHost == host) {
            cookies.addAll(CookieString(SettingsRepository.current.cloudFlareCookie).toLoginCookieList(host))
        }
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieMap[url.host] = cookies.toMutableList().also {
            it += CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(url.host)
        }
    }
}
```

`toLoginCookieList()` 做逐字符白名单清洗（保留 `0x20..0x7E`，剔除 `\n` / `\r`），`Cookie.Builder` 失败时记日志而非抛异常。另有一个 `preferencesCookieList()` 始终注入 `user_lang` Cookie，**让视频语言偏好不被登出清掉**。

源码注释记录了这段历史（`#issue-71`）：

> *我竟然栽倒在 Cookie 管理上好幾年了！你去看我以前的管理方式，是完全錯誤的……怪不得切換簡體繁體一直不起作用！*

### 8.6 签名校验（非网络，但同库）

`chino.cpp` 里除 H3 外还有一套 **APK 签名自校验**：从 `/proc/self/maps` 找 `base.apk` 路径（走手写 `svc #0` 系统调用，避开 libc hook），解析 ZIP EOCD → APK Signing Block（ID `0x7109871a`）→ 提取签名证书 → 自实现 SHA-256（`kaffu.c`）→ 与 `chino.h` 的 `EXPECTED_SIG_HASH` 比较。

Kotlin 侧（`ui/screen/video/VideoRouteHostScreen.kt:1097-1098`）：

```kotlin
private external fun svc(): Boolean       // 校验通过？
private external fun getString(): String  // 返回实际哈希（排障用）
```

Debug 版不启用（`showDialog = !BuildConfig.DEBUG && !svc()`）。用途是阻止第三方重新签名分发。`正式版构建行动指南.md` 有完整的密钥/回填流程。

---

## 九、设置与热更新

### 9.1 设置存储

`SettingsRepository`（`logic/SettingsRepository.kt`）包装 DataStore（`DataStoreManager`），模型为 `logic/model/AppSettings.kt` 的 data class。`HanimeApplication.onCreate()` 顺序：

```kotlin
DataStoreManager.initialize(this)
SettingsRepository.install(DataStoreManager)
if (SettingsRepository.useEch) ConscryptEch.install()
HyEchH3.attach(this)
ProxySelector.setDefault(HProxySelector())
HProxySelector.rebuildNetwork()
```

### 9.2 变更后的重建矩阵

| 改动项 | 需要执行 |
|---|---|
| DoH 设置（`useDoH` / `dohPreset` / `dohCustomUrl` / `dohBootstrapIps` / `dohTimeoutSeconds`） | `EchHttp.onDohSettingsChanged()` + `HanimeNetwork.rebuildNetwork()` + `SingletonImageLoader.reset()` |
| `useEch` | 同上 |
| 代理（type / ip / port） | `HProxySelector.rebuildNetwork()` + `HanimeNetwork.rebuildNetwork()` + `SingletonImageLoader.reset()` |
| 自定义 hosts | 仅 `if (useBuiltInHosts) HanimeNetwork.rebuildNetwork()` |
| 域名 / 镜像站 | `logout()` + `ActivityManager.restart(killProcess = true)`（**必须重启进程**） |
| 备用媒体 CDN / 内置 hosts 开关 | 弹窗提示重启（`restart_or_not_working`） |

### 9.3 设置页自带的网络诊断

`NetworkSettingsRoute.kt` 提供三套测试，均用 `Executors.newCachedThreadPool()` + `Handler(Looper.getMainLooper())` 回主线程：

| 功能 | 实现 |
|---|---|
| 节点延迟测试 | `HDns().getCDNList(host)` 列候选 → 每 2 秒轮询 `InetAddress.isReachable(2000)` |
| DoH 测试 | `HDns().lookupByDoHOnly(host)`，记录 IP 与耗时 |
| 自定义镜像站测试 | 先 GET `homeUrl` → `Parser.homePageVer2` 验证可解析 → 再 GET `apiBaseUrl + "search"` 验证 API 可用 |

镜像站 URL 校验很严（`normalizeCustomMirrorSite`）：**必须 https、必须有 host、不得带 query 或 fragment**。

---

## 十、异常体系

**目录**：`logic/exception/`（6 个文件）

| 异常 | 触发点 |
|---|---|
| `IPBlockedException` | 403 + body 含 `you have been blocked` |
| `CloudflareBlockedException` | 403 + body 含 `Just a moment` |
| `HanimeNotFoundException` | 403 其他 / 500 |
| `LoginStateExpiredException` | `Parser.homePageVer2` 检测到用户名或主页链接异常 |
| `NotLoggedInException` | 需要登录的操作 |
| `ParseException` | `Parser` 解析失败；`handleException` 会重包装为统一文案 |

**消费侧**：`util/Networks.kt::toNetworkErrorMessageRes()` 把异常映射为字符串资源 ID，供首页展示：

| 条件 | 资源 |
|---|---|
| `UnknownHostException` / "unable to resolve host" / "no address associated with hostname" | `home_error_dns` |
| `SocketTimeoutException` / "timeout" | `home_error_timeout` |
| `SSLHandshakeException` / "ssl" / "certificate" | `home_error_ssl` |
| `ConnectException` / "failed to connect" | `home_error_connect` |
| `SocketException` + "connection reset" | `home_error_connection_interrupted` |
| `IPBlockedException` | `cloudflare_ip_block_warning` |
| `CloudflareBlockedException` | `cloudflare_network_mismatch` |
| … | … |

同文件还有 `Call.await()`（`suspendCancellableCoroutine` + `invokeOnCancellation { cancel() }`）—— 下载 Worker 用它在协程里 await OkHttp Call。

---

## 十一、已知缺陷与风险

移植时需注意或修正：

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| 1 | `HanimeNetwork.kt:43-49` | `rebuildNetwork()` **漏重建 `subscriptionService`**，只更新 4 个 | 改网络设置后订阅接口仍用旧 client（旧 DNS/代理/传输） |
| 2 | `HCookieJar.kt:25` | `cookieMap` 是 **companion 静态全局**，`rebuildNetwork()` 与切换域名时均不清理 | 换域名后旧 host 的 Cookie 残留；进程内永久驻留 |
| 3 | `HDns.kt:173-192` | `getCDNList()` 在 DoH 开启时**仍只走 `Dns.SYSTEM`** | 设置页延迟测试显示的 IP 与实际请求解析路径不一致 |
| 4 | `EchDoh.kt:184-193` | `isCloudflareHost()` 解析失败时返回 `true`（保守） | 非 Cloudflare 域也会尝试 ECH，触发失败冷却 30s |
| 5 | `ServiceCreator.kt` | 三个 client **均未配 `readTimeout`** | 用 OkHttp 默认 10s，慢速下载/长响应可能超时（下载另有 `SpeedLimitResponseBody` 拖慢读取，风险更高） |
| 6 | `EchDoh.kt:210-215` | `invalidateEch(host)` 里 `echCache.remove(LIVE_SOURCE_HOST)` 会对**所有 host 生效** | 一个 host 刷新会顺带作废共享源，可能造成不必要的重复查询 |
| 7 | `HyWebViewHelper.kt:124` | 失败重试前 `Thread.sleep(300)` 在调用线程（WebView 的 IO 线程）上阻塞 | 局部阻塞 300ms |
| 8 | `ConscryptEch.kt` | `ready` 只增不减，`install()` 失败后再次调用才会重试成功 | 首次失败后到下次 `install()` 之间 ECH 不可用 |

---

## 十二、与 `Han1meViewer-main`（旧路线）的差异

`reference/` 下另有 `Han1meViewer-main`，网络架构走的是完全不同的路线：

| 维度 | `Han1meViewer`（本文档） | `Han1meViewer-main` |
|---|---|---|
| ECH 载体 | **进程内** Conscrypt（TLS）+ Rust quiche（QUIC） | **Go gomobile** 编译的 `echproxy` AAR，起 `127.0.0.1:<port>` 本地代理 |
| 代码位置 | `logic/network/ech/`（12 文件） | `logic/ech/EchProxyManager.kt` |
| 配置来源 | `cloudflare-ech.com` 的 HTTPS 记录 + DoH | 远程 DNS TXT `ech-config.anglesgirl.eu.org`（下发 doh/doh2/doh3/ip）+ 本地预设 |
| 失败兜底 | 核心域 fail-closed / 其他域明文 | 握手失败自动兜底一次，再失败降级普通 TLS |
| 缓存 | DoH 5min / ECH 1~5h / 失败 30s | ECH 公钥配置缓存 5h |
| H3 | Rust quiche 静态链入 `libchino.so` | 无 |

**两者不可混用**。移植时若目标是 KMP 多平台，`Han1meViewer` 的进程内方案更可控（无本地端口、无 AAR），但 Conscrypt 与 Rust NDK 都是 Android 专属，需要按平台拆分。

---

## 附录 A：关键文件清单

### A.1 网络核心（`logic/network/`，共 30 文件 = 顶层 7 + interceptor 6 + service 5 + ech 12）

```text
logic/network/
├── ServiceCreator.kt                 3 个 OkHttpClient 装配 + 热重建      111 行
├── HanimeNetwork.kt                  5 个 Retrofit Service 聚合 + rebuild  50 行
├── HDns.kt                           三级降级 DNS + DoH + 自定义 hosts    212 行
├── DohConfig.kt                      4 个 DoH 预设 + 自定义                70 行
├── HCookieJar.kt                     Cookie 管理（静态 map + 持久化注入）   48 行
├── HProxySelector.kt                 Direct/System/HTTP/SOCKS + 全局代理   98 行
├── CloudflareVerificationCoordinator.kt  host 去重 + CountDownLatch        75 行
├── interceptor/
│   ├── CloudflareInterceptor.kt      403 + cf-mitigated: challenge          30 行
│   ├── UserAgentInterceptor.kt       伪装 Chrome 149 Mobile                 14 行
│   ├── UrlLoggingInterceptor.kt      解码后打印完整 URL                     17 行
│   ├── SpeedLimitInterceptor.kt      包装 body 限速                         23 行
│   ├── SpeedLimitResponseBody.kt     okio Throttler                         29 行
│   └── GetchuInterceptor.kt          桌面 UA + gc Cookie + ja               19 行
├── service/
│   ├── HanimeBaseService.kt                                                  58 行
│   ├── HanimeCommentService.kt
│   ├── HanimeMyListService.kt
│   ├── HanimeSubscriptionService.kt
│   └── GetchuService.kt                                                      60 行
└── ech/
    ├── EchHttp.kt                    echTransport() 扩展 + 缓存失效         25 行
    ├── EchDoh.kt                     手写 DNS wire + SVCB 解析 + ASN 校验  394 行
    ├── ConscryptEch.kt               TLS 注入 + PolicyTrustManager + 重试  215 行
    ├── EchDns.kt（在 EchDoh.kt 内）  DoH 优先 + fail-closed
    ├── EchHosts.kt                   核心域判定                              22 行
    ├── EchTransportPolicy.kt         fail-closed / H3 策略                  25 行
    ├── HyEchH3.kt                    JNI 包装 + CA 导出 + 冷却              210 行
    ├── H3Interceptor.kt              静态资源 QUIC 拦截                     64 行
    ├── HyWebViewHelper.kt            WebView 三层接管之一                   213 行
    ├── EchWebBridge.kt               @JavascriptInterface 原生桥            260 行
    ├── EchWebBridgeJs.kt             300 行注入脚本                         310 行
    ├── EchDiagnostics.kt             四步诊断                               135 行
    └── EchLog.kt                     内存环形日志
```

### A.2 原生与构建

```text
native-h3/
├── Cargo.toml                        crate hn1_h3
├── src/lib.rs                        quiche H3 + JNI h3Fetch              329 行
└── vendor/quiche/                    quiche 0.22（sh 脚本拉取+打补丁）

app/src/main/cpp/
├── CMakeLists.txt                    whole-archive 链入 + version script
├── chino.cpp                         APK 签名校验（手写 svc 系统调用）     317 行
├── chino.h                           EXPECTED_SIG_HASH
├── chino.exports                     version script（只导出 2 个符号）
├── kaffu.c / kaffu.h                 自实现 SHA-256

buildSrc/src/main/java/EchH3.kt       NDK/CMake/cargo 任务链               223 行
tools/prepare_quiche.py               下载/校验/解压/打补丁
tools/verify_ech.py                   bridge / apk 两段校验
```

### A.3 仓库层与工具

```text
logic/NetworkRepo.kt                  主仓库，flow 三件套 + 异常映射        633 行
logic/GetchuNetworkRepo.kt            Getchu 仓库（EUC-JP 解码）            92 行
logic/Parser.kt                       Jsoup 解析 + CDN 节点修正
logic/GetchuParser.kt                 Getchu 解析
util/Networks.kt                      Call.await + 错误文案映射            118 行
util/Cookies.kt                       CookieString + 清洗 + 偏好 Cookie     58 行
HanimeApplication.kt                  Coil ImageLoader + 全局初始化        154 行
Constants.kt                          BASE_URL / HOSTNAME / CDN / UA       99 行
```

---

## 附录 B：网络配置项清单

来源：`logic/model/AppSettings.kt:111-135`

| 字段 | 默认值 | 用途 |
|---|---|---|
| `loginCookie` | `""` | 持久化登录 Cookie |
| `cloudFlareCookie` / `cloudFlareCookieHost` | `""` / `""` | Cloudflare clearance Cookie 与对应 host |
| `domainName` / `selectedBaseUrl` | `https://hanime1.me/` | 主站域名 |
| `useCustomMirrorSite` / `customMirrorSite` / `appendCustomMirrorPath` | `false` / `""` / `true` | 自定义镜像站 |
| `useBuiltInHosts` | `false` | 启用内置 Cloudflare IP 池 / 自定义 hosts |
| `customHostsData` | `""` | 逗号分隔的自定义 IP |
| `useBackupMediaCdn` | `false` | 媒体 CDN 换 CDN77 |
| `useDoH` | `false` | 启用 DoH |
| `useEch` | `false` | **启用 ECH（含 QUIC/H3）** |
| `dohPreset` | `"alidns"` | `alidns` / `dnspod` / `cloudflare` / `ech_gateway` / `custom` |
| `dohCustomUrl` | `""` | 自定义 DoH URL |
| `dohBootstrapIps` | `""` | DoH 引导 IP（逗号/换行/分号/空格分隔） |
| `dohTimeoutSeconds` | `10` | `coerceIn(1, 60)` |
| `proxyType` | `ProxyType.System` | Direct / System / HTTP / SOCKS |
| `proxyIp` / `proxyPort` | `""` / `-1` | 代理地址 |
| `downloadSpeedLimitIndex` | `0` | `DOWNLOAD_SPEED_BYTES` 下标，0 = 不限速 |
| `downloadCountLimit` | `2` | 并行下载数 |

**互斥关系**：`useBuiltInHosts` ⟂ `useDoH`（设置页强制二选一）。
**联动关系**：`useEch = true` 时 `useDoH` 仍独立生效（ECH 链在 `useDoH == false` 时只用纯 IP 端点）。
