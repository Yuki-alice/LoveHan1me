# 网络与加载优化（活计划）

- 来源：2026-10-10 与参考项目 `reference/Han1meViewer` 的网络层对比分析。
- 本文件是**唯一活计划**：每完成一个阶段更新状态总览与证据；全部收尾后归档。
- 立项：2026-10-10。

## 背景结论（对比分析摘要）

当前项目连接层整体强于参考项目（跨端 Go 网关、按域熔断、调度预算、CF 验证续跑、stale 首屏），
但存在三类结构性缺口：

1. **iOS 出口能力断层**：应用内 HTTP/SOCKS 代理不可用（`ProxyCapability.ios.kt`）；自定义
   DNS / hosts 不可配（设置页如实标注）；网关冷启动无"拉起中"等待（`EchGatePlugin.kt:66-69`
   传 null）；图片链无超时/重试；错误文案映射在 iOS 上因 Darwin 措辞与本地化而失真
   （`NetworkError.kt:26-80` 全为 message 关键字匹配，关键字是 OkHttp 措辞）。
2. **同一逻辑双实现**：`EchGateInterceptor`（jvmMain）与 `EchGateClientPlugin`（commonMain）
   各约 300 行，已出现刻意差异（502 判定、就绪等待、代理步）——**本计划不处理，另立方向 2**。
3. **加载语义不统一**：四类加载状态并存（使用面 WebsiteState 46 / PageLoadingState 34 /
   VideoLoadingState 12 / PageState 9 文件）；stale-first 缓存每页手写；错误页无出口逃生舱。

## 状态总览

| # | 阶段 | 依赖 | 状态 | 证据 |
|---|------|------|------|------|
| 1.1 | iOS 传输层统一装配 + 图片链超时/重试 | — | ✅* | `createIosTransport` 一处装配（ech/IosTransport.kt），5 处裸 Darwin client 收编；图片链补 30s call 档 + 同谓词重试；存相册 client 进程级单例。`grep HttpClient(Darwin)` 生产代码零残留；三端编译 + desktopTest 绿。⚠️ iOS 冒烟（封面/getchu 图/存相册/下载）待真机 |
| 1.2 | iOS 应用内代理（HTTP/SOCKS） | 1.1 | ✅* | `proxyDictionaryFor` 四模式 + 例外表恒含回环（键用字符串字面量——C 常量 iOS SDK 未导出，值即其名）；`createIosTransport` 构造期注入；`HanimeNetwork.rebuildTransports()` + `platformRebuildSystemProxy` iOS actual 接管（改代理/备份导入即生效）；设置页文案改即时生效。`ProxyDictionaryTest` 6 用例（iosSimulatorArm64Test 绿）。⚠️ 真机手工验收未做：HTTP 代理请求出现在代理日志 / Direct 空字典能否真关系统代理 / SOCKS 兼容性（预案见阶段 1.2）|
| 1.3 | iOS 冷启动网关就绪等待 | — | ✅* | `EchGatePortReporter.markStarting()`（Swift 起服前落拉起中，失败路径显式落 Stopped）+ `IosGateReadiness`（单飞有界等待 5s，onStatusChanged 钩子唤醒 + 双检防丢唤）+ `installEchGate` 注入 `awaitGateReady`。`IosGateReadinessTest` 5 用例（模拟器绿）。⚠️ 冷启动日志"等待而非抢跑"待真机确认 |
| 1.4 | 错误分类结构化 NetworkFailure | — | ✅* | common `NetworkFailure` sealed（Dns/Timeout/Ssl/Connect/ConnectionReset/Offline/Http/Parse/NoRoute/Other）+ `toNetworkFailure()` 三层判定 + jvm/ios actual；文案映射签名/资源不变，Other 走旧关键词兜底零回归。commonTest 13 + desktopTest 6 + iosTest 9 用例全绿（iOS 断网 -1009 → 连接中断，动机用例）。⚠️ iOS 飞行模式/桌面断网目视未验 |
| 3.1 | stale-first 缓存下沉 | — | ✅ | 4 expect 收敛为 `readCachedHtml`/`writeCachedHtml`（清理按 key 前缀自理）+ `staleFirstFlow` 薄骨架（stale/fresh 双类型分派，闸门/合并/刷新留在 VM）；首页/发现页迁移。desktopTest 真文件系统用例（前缀清理不串文件/尺寸护栏/路径逃逸）+ 全量绿 |
| 3.2 | NetworkRepo 请求骨架收敛 | — | ✅ | websiteIOFlow/pageIOFlow 合一为 `stateIOFlow`（errorOf 绑定状态构造器），行为等价清单（emit 时机/onBody 位置/catch/flowOn/permittedSuccessCode）逐项对照；videoIOFlow 保留 PlayerTrace 薄封装。desktopTest 全量绿 |
| 3.3 | 错误页出口逃生舱 | 1.4 | ✅* | `ErrorContent.secondaryActions` 插槽（默认 null 零改动，向后兼容）；首页错误态注入强制直连/强制网关/恢复自动（写 egressForceMode + rebuildSystemProxy 复位熔断 + 重载）。三端编译 + desktopTest 绿。⚠️ 模拟被墙 → 点按钮恢复的手工验收待做 |

推荐执行顺序：**1.1 → 1.4 → 3.1 → 1.2 → 1.3 → 3.2 → 3.3**。
（先统一 iOS 装配以解锁 1.2 并顺带修复图片链；错误分类独立且解锁 3.3；缓存下沉用户可感知、
低风险；随后是代理、就绪等待、内部重构、逃生舱收尾。）

> **2026-10-25 收口**：七阶段按推荐顺序全部落地（各阶段独立提交，`feat(net)` / `refactor(net)` / `feat(ui)`）。
> ⚠️ 标记项 = 完成判据里的真机/手工验收仍未做，随发布前实测一并收口；iOS 手工验收清单见各阶段原文。

## 验收协议（每阶段走完才算完成）

1. **三端编译 + 桌面测试**：`./gradlew --offline :shared:compileKotlinDesktop
   :shared:compileAndroidMain :shared:compileKotlinIosArm64 :shared:desktopTest`；
   含 iOS 逻辑的另跑 `:shared:iosSimulatorArm64Test`（模拟器）。
2. **行为声明**：纯重构须逐项对照"行为等价清单"；有意行为差异须列新旧差异与理由。
3. **手工验收**：iOS 阶段按各阶段"手工验收"清单在真机/模拟器逐项打勾，结果写回本文件。
4. **独立提交**：阶段粒度 commit，保持自洽可编译，可单独 revert。
5. **证据回写**：测试名 / 命令 / 日志要点更新到状态总览与分阶段记录。

## 关键技术核验（2026-10-10 本地已验证，避免重复推导）

| 结论 | 证据 |
|---|---|
| Ktor 3.5.2 Darwin 引擎有 `configureSession`（NSURLSessionConfiguration）/ `configureRequest` / `preconfiguredSession` | ktor-client-darwin-iosarm64-3.5.2 klib 符号 + api.ktor.io |
| `DarwinHttpRequestException` 公开 `origin: NSError` | api.ktor.io 类成员表 |
| K/N Foundation 含 `NSURLError*` 常量与 `connectionProxyDictionary`；CFNetwork klib 含 `kCFNetworkProxies{HTTP,HTTPS,SOCKS}{Enable,Proxy,Port}` 与 `ExceptionsList` | ~/.konan platform klib 符号 |
| iOS 网关由 Swift 进程内起服（gomobile），端口经 `EchGatePortReporter.setPort` 回填；**当前无 Starting 标记** | `EchGateBootstrap.swift:17-53`、`EchGate.kt:96-115` |
| `BridgeCookiesStorage` 无实例状态（状态在 `IosCookieBridge` + DataStore）→ 客户端重建不丢 Cookie | `IosCookieBridge.kt:92-117` |
| JVM 侧已有等价机制可镜像：`EchGateRuntime.STARTUP_GRACE_MS=8s`、`awaitReadyIfStarting`、`EchGate.onStatusChanged` 钩子（iOS 侧该钩子空闲） | `EchGateRuntime.kt:63-111`、`EchGate.kt:99` |

## 方向 1：iOS 出口能力补齐

### 目标与边界

iOS 单端补齐"应用内代理 + 冷启动就绪 + 错误分类"，并把图片 / 下载 / API 三条链统一到同一
装配器；JVM 行为保持不变（除错误分类映射共用）。

### 设计决策

1. **代理 = NSURLSession 会话级注入**（iOS 唯一可行点，经 `configureSession`）。
   映射：`Direct` → 空字典（关系统代理，存疑项见风险）；`System` → 不设置（跟随系统）；
   `Http` → HTTP+HTTPS 键；`Socks` → SOCKS 键；`ExceptionsList` 恒含 `127.0.0.1` / `localhost`
   （网关回环必须绕过代理）。
2. **生效 = 重建客户端**：会话配置在创建时被拷贝。`HanimeNetwork` 增加
   `internal fun rebuildTransports()`（重建 2 个客户端 + 5 个 service；services 改 `var` +
   `private set`）。iOS 三个触发点：设置页应用代理、`rebuildSystemProxy()` iOS actual
   （现为 no-op）、备份导入（已在调后者）。**JVM 无调用点，稳定单例语义不变**；
   调度器无需改（`currentProxyState()` 已是 common 判定，配好即进入"网关试用/让位"语义）。
3. **统一 iOS 传输装配器** `createIosTransport(...)`（iosMain）：engine(configureSession) +
   installEchGate + 可选 HttpCookies + HttpTimeout + HttpRequestRetry；替换 5 处手写客户端。
4. **就绪等待**：Swift 起服前调 `markStarting()`；iosMain 新增 `IosGateReadiness`
   （挂 `EchGate.onStatusChanged` 钩子，单飞 + 有界等待，仅 `useEchGate` 开且 `Starting` 时
   等待，上限初值 5s、以实测定档）；经 `installEchGate(awaitGateReady=)` 注入。
5. **错误分类**：common 新增 `NetworkFailure` + `Throwable.toNetworkFailure()` +
   `expect fun Throwable.platformNetworkFailure()`；`toNetworkErrorMessageRes()` 签名不变、
   内部改走分类（现有调用点零改动）。

### 阶段 1.1 iOS 传输层统一装配（前置，独立）

- 新增 `shared/src/iosMain/.../data/network/IosTransport.kt`：`createIosTransport(...)`。
- 接入 5 处手写客户端：
  - `HttpClientFactory.ios.kt:28-55`（hanime / getchu；plain 保持裸配置，与 JVM 语义对齐）；
  - `HanimeImageLoader.ios.kt:42-48`（补 30s 档超时 + 同谓词重试，对齐 `EgressBudgets.Image`）；
  - `GetchuImageLoader.ios.kt:36-39`（同上）；
  - `HomePageUtils.ios.kt:17-24`（存相册：改进程级单例，不再每次建/close）；
  - `IosDownloadWorkController.kt:489-507`（保留自身超时档 connect15/socket30，接入公共装配）。
- **验收**：
  - grep 通过：生产代码无裸 `HttpClient(Darwin)`（plain 与测试除外）；
  - iOS 冒烟：首页封面 / getchu 图预览 / 公告图存相册 / 视频下载正常；
  - 三端编译 + desktopTest 不受影响（纯 iOS 改动）。
- **风险**：低（无行为语义变化；图片链时长档与 JVM 对齐需目测无回归）。

### 阶段 1.2 iOS 应用内代理（核心）

- 任务：
  1. `proxyDictionaryFor(type, ip, port): Map<Any?, Any?>?` 纯函数（iosMain，含 ExceptionsList）；
  2. `createIosTransport` 经 `configureSession` 注入；
  3. `HanimeNetwork.rebuildTransports()` + 三个触发点（见设计决策 2）；
  4. iOS 设置页：把 `NetworkSettingsScreen.kt:457-539` 的 `ProxyDialog` 从 `private` 提为
     `internal` 复用；`PlatformSettingsRoutes.ios.kt` 增加代理入口行（summary 显示当前代理）；
  5. 文案：`network_platform_limits_ios` 删除"代理不可用"表述，补边界说明
     （应用内代理支持 HTTP/SOCKS；系统代理由系统设置决定）。
- **验收**：
  - 单测（`iosSimulatorArm64Test`）：字典构造断言（HTTP/SOCKS/Direct/System 四模式 +
     例外表恒含回环）；
  - 手工：配 HTTP 代理后请求出现在代理端日志；切 Socks 同；网关回环流量不经代理；
     改代理**不重启即生效**；备份导入后生效。
- **风险与降级预案**：`Direct` 空字典能否真正关闭系统代理不确定 → 验收不过则降级为
  "跟随系统" + 文案如实；SOCKS 在 NSURLSession 的兼容性不通过 → 先只支持 HTTP，文案标注。

### 阶段 1.3 iOS 冷启动网关就绪等待

- 任务：
  1. `EchGateBootstrap.swift` 起服前调 `EchGatePortReporter.shared.markStarting()`；
  2. `EchGatePortReporter` 增 `markStarting()`（publish `EchGateStatus.Starting`）；
  3. iosMain 新增 `IosGateReadiness`（单飞 + 有界等待，镜像 JVM `awaitReadyIfStarting` 语义）；
  4. `installEchGate` 注入 `awaitGateReady`。
- **验收**：冷启动日志：首个请求早于网关就绪时产生"等待"而非直连抢跑；网关关闭 / 失败时
  **零额外等待**；并发首屏只有一个请求在等待（单飞）。
- **风险**：等待引入首屏延迟 → 上限可调（初值 5s）+ 前置条件收紧（仅开关开且 Starting）。

### 阶段 1.4 错误分类结构化

- 任务：
  1. common `NetworkFailure`：`Dns / Timeout / Ssl / Connect / ConnectionReset / Offline /
     Http(code) / Parse / NoRoute / Other(cause)`；
  2. `Throwable.toNetworkFailure()`：先 common 判定（`HttpRequestTimeoutException`→Timeout、
     `ParseException`→Parse、`NoRouteException`→NoRoute、Cancellation 透传），再调
     `platformNetworkFailure()`；
  3. jvmMain：类型分支（`UnknownHostException`→Dns、`SocketTimeoutException`→Timeout、
     `SSLException/SSLHandshakeException`→Ssl、`ConnectException`→Connect）；
  4. iosMain：`DarwinHttpRequestException.origin.code` → -1003→Dns、-1001→Timeout、
     -1004→Connect、-1005/-1009→Offline/Reset、-1200/-1202→Ssl；
  5. `toNetworkErrorMessageRes()` 内部改用分类，签名与文案资源不变。
- **验收**：新增 commonTest（common 分支）+ desktopTest（jvm 类型分支）+
  iosSimulatorArm64Test（构造 `NSError` → `DarwinHttpRequestException` 断言）；
  手工：iOS 飞行模式 / 关站显示 DNS / 连接类而非 generic；桌面断网显示 DNS。
- **风险**：低（纯映射新增；旧关键字匹配作为 `Other` 兜底保留）。

## 方向 3：加载语义统一

### 范围决策

- **做**：3.1 缓存下沉、3.2 请求骨架收敛、3.3 错误页逃生舱。
- **不做：四类状态全量合并**。使用面 WebsiteState 46 / PageLoadingState 34 /
  VideoLoadingState 12 / PageState 9 文件，全量合并 churn 巨大、收益概念化。改为
  **单向收敛 + 冻结**：`core/domain/state` 包 KDoc 写明四类边界与目标类型；新代码只用
  `PageState`；存量随需求改动顺带迁移（VideoLoadingState 为第一候选）。

### 阶段 3.1 stale-first 缓存下沉（先做）

- 任务：
  1. 存储层收敛：4 个 expect fun（`read/writeCachedHomeHtml`、`read/writeCachedDiscoverHtml`）
     → `readCachedHtml(key)` / `writeCachedHtml(key, html)`；清理规则改为按
     `key.substringBefore('_')` 前缀（现为写死的 `home_` / `discover_`，见
     `HomePageCache.jvm.kt:26,40`）；jvm / ios 两个 actual 合并实现；
  2. 保留 key 生成（`cacheKeyFor` / `homePageCacheKey` / `discoverCacheKey`）；
  3. 新增薄骨架 `staleFirstFlow(readStale, network)`（common），home / search 迁移；
     token / merge / refresh 语义留在各自 VM（`SearchViewModel` 的 PagingGate 判定不进骨架）。
- **验收**：`HomePageCacheKeyTest` 保持绿；新增"前缀清理不串文件"测试（desktopTest 真文件系统）；
  手工：冷启动先出内容再刷新行为不变；grep 旧函数零残留。
- **风险**：清理规则变更 → 测试盯住切站 / 换号旧文件清理。

### 阶段 3.2 NetworkRepo 请求骨架收敛

- 任务：`websiteIOFlow` / `pageIOFlow` / `videoIOFlow`（`NetworkRepo.kt:647-699`）合一为
  `stateIOFlow`；`videoIOFlow` 保留为 PlayerTrace 薄封装。
- **行为等价清单**（必须逐项对照）：emit 时机、`onBody` 位置（action 之前）、`catch` 顺序
  与错误映射、`flowOn(ioDispatcher)`、`permittedSuccessCode` 语义。
- **验收**：diff 对照 + desktopTest + 手工路径：首页 / 搜索 / 视频详情 / 评论 / 登录。
- **风险**：低（单文件内部重构）。

### 阶段 3.3 错误页出口逃生舱（依赖 1.4）

- 任务：`ErrorContent`（`ui/component/content/ErrorContent.kt`）增加 optional
  `secondaryActions` 插槽（默认空，向后兼容）；首页错误态（`SharedHomeScreen.kt:173-188`）
  注入"强制直连 / 强制网关 / 恢复自动"：写 `SettingsRepository.egressForceMode` +
  `RouteRegistry.reset()` + 重新加载。仅首页先做，其他页面复用 `PageContent` 后续自然获得。
- **验收**：模拟被墙（ForceDirect 预期撞 RST）→ 错误页出现按钮 → 点"强制网关"后恢复；
  现有错误文案与重试行为不变。
- **风险**：低。

## 明确不做（范围外，另立）

- **方向 2 双执行器收敛**（`EchGateInterceptor` × `EchGateClientPlugin` 合一）——另立计划。
- iOS 移植 JVM 的 DoH / 内置 IP / CdnIpProbe——网关路径已覆盖大部分场景，仅"网关未运行"时
  残留，暂不做。
- HTTP/3（参考项目 native-h3）——评估后置。
- 下载限速（参考有、当前缺，且 `CdnFetchClient.kt:25` 注释与实现不符，`ServiceCreator.kt:118-133`
  实际未挂限速）、plain client 超时表——P2 项另立。
- 四类状态类型的存量全量迁移——按方向 3"范围决策"冻结处理。