# 决策记录

> 本文件当前保存 2026-10-02 起的条目。更早的条目仍完整留在 git 历史里
> （`git checkout HEAD -- docs/decisions.md` 可取回并合并）。

## 登录态独立存储（2026-10-06，P0 数据隐私）

- **背景**：手动备份以 `AUTH_KEYS` 排除登录态 6 键，但 Android 系统备份规则
  只能按文件排除、不能按 key 排除，而 6 键与 90 个普通设置混在同一个
  `settings.preferences_pb` 里 —— 系统备份形同虚设（cookie/cf/弹幕密钥明文漫游）。
- **改动**：新增 `auth.preferences_pb` 独立存 6 键（读写合并视图、写侧分流、
  启动时老键一次性迁移、冲突以独立存为准、幂等）；两份 xml 只排除该文件，
  普通设置照常漫游；手动备份的 `AUTH_KEYS` 过滤保留（双保险）。
  守卫 `AuthStoreMigrationTest` 4 例（真文件实测迁移/冲突/合并/空安装）。
  失效条件＝DataStore 换存储后端（合并/迁移语义需重写），或决定登录态可漫游
  （与手动备份口径同步改）。重认日期 2027-04-06。

## 下载限速移除（2026-10-06，用户决策）

- **移除上游带来的下载限速整套**：`SpeedLimitInterceptor` /
  `SpeedLimitResponseBody`、下载设置页档位 UI、`AppSettings.downloadSpeedLimitIndex`
  + `DOWNLOAD_SPEED_BYTES`、`SettingsRepository` 读写口、DataStore 读写、
  中英繁 `download_speed_limit` 文案。理由：窄场景（移动数据后台下载不抢带宽），
  投入产出比不如下载调度本身；此前桌面/iOS 侧本就是半截实现。
  存量迁移＝无：老设备残留键不再读取、无害忽略；旧备份同理。
  失效条件＝有用户明确要"后台下载不抢带宽"（届时按三端同时可验的标准重做，
  不接受单端半截）。重认日期 2027-04-06。

## 网络：本地 ECH 网关的 Android 接入形态

- **Android 的网关运行时落在 `:app` 壳，经 jvm 共享门面接入**。门面 `EchGateRuntime`
  （`shared/src/jvmMain/.../EchGateRuntime.kt`）持有与"进程"无关的骨架：
  single-flight 就绪等待、8s 启动宽限、30s 自愈节流、就绪信号唤醒、状态落点。
  平台只在 `EchGateStarter` 处分叉 —— 桌面 `DesktopEchGateStarter`（独立 exe，
  自 jvmMain 迁至 `shared/src/desktopMain`）、Android `AndroidEchGateStarter`
  （`:app`，gomobile 进程内起服）。消费方（拦截器 / 设置页 / 站点切换）只认门面，
  不再引用任何具体运行时。
  **为什么 Android 侧不放在 `:shared/androidMain`**：AAR 只能由 application 模块
  以本地文件依赖接入（AGP 拒绝 library 模块的本地 `.aar`，而 KMP 的 android 目标
  正是一个 library），所以引用 `gate.*` 的代码只能在 `:app`；这与 iOS 把起服放在
  壳工程（`EchGateBootstrap.swift`）是同一种分工。
  失效条件＝`gradle/libs.versions.toml` 的 AGP 升级到"支持 library 模块消费本地 AAR"
  之后（或网关改为按坐标发布到本地 maven 仓），starter 可下沉 `:shared/androidMain`；
  或 iOS 壳把起服下沉 shared 时，三端分工需重新对齐。重认日期 2027-04-02。

- **验证窗（CF 人机验证）在 Android 也把出站覆盖到网关的 CONNECT 隧道**（D1 候选 a）。
  判据复用 `GateState.connectTunnelUrl()`（`enabled && running && !circuitOpen`），
  与桌面 `CloudflareCdp.proxyFlag` 的 `--proxy-server` 同源，不产生第二处判定；
  实现走 `androidx.webkit` 的 `ProxyController`（`WebViewFeature.PROXY_OVERRIDE`
  门控，库侧自 1.1.0 起提供），不支持时原样加载＝接入前行为。
  **它解决的是"同出口"**：`cf_clearance` 绑 UA + 出口 IP，App 经网关出站的出口是
  本机 IP，而 WebView 默认走系统代理＝代理 IP，两边不一致就会"验证过了仍然要验证"。
  **它不解决 SNI 阻断下验证页自身加载不了**：CONNECT 隧道只做 DoH 解析 + 裸 TCP，
  客户端在隧道内自己做 TLS、SNI 仍是明文，网关帮不上 ECH 的忙（见 `echgate/main.go`
  的"两条通道"一节）。这一条三端同构、属既有事实，故"验证页加载失败时给引导"仍可
  作为独立增量另立一项。
  失效条件＝网关的 CONNECT 隧道被删除或不再做 DoH 解析；或 `androidx.webkit` 依赖被移除。
  重认日期 2027-04-02。

- **验证窗（CF 人机验证）的出站覆盖按门面状态分两支**。网关是可用候选 ⇒
  `CONNECT 隧道 + addDirect()`；否则 ⇒ **用户手填代理**（Http / Socks），未配则不覆盖
  （＝系统代理语义）。判据全部取自 `currentEgressState()`，与拦截器 / 播放器 / 图片链同一份快照。
  **刻意不把"用户代理"放在隧道的失败兜底里**：网关是候选时 App 的出口是本机 IP
  （代理选择器对回环短路），隧道坏掉落到直连仍是本机 IP；若改落用户代理，出口变成代理 IP、
  与 App 反而不一致，正是"验证过了仍然要验证"的成因（`cf_clearance` 绑 UA + 出口 IP）。
  失效条件＝桌面 `CloudflareCdp.proxyFlag` 的口径变化（两端必须同步改），或
  `ProxyConfig.addProxyRule` 不再接受 http / socks scheme。重认日期 2027-04-02。

- **网关联产物与 AAR 成对入库**：`app/libs/Echgate.aar` 与 gomobile 顺带产出的
  `Echgate-sources.jar` 一起提交（IDE 可对 AAR 做 source attach）；`.gitignore` 不拦
  `app/libs/`。改网关源码后必须重跑 `echgate/build-android.sh` 并把两者一起提交。
  失效条件＝改为按坐标发布到 maven 仓（sources jar 应改用 sources classifier），
  或不再入库二进制。重认日期 2027-04-02。

- **明文的唯一豁免是回环**：`app/src/main/AndroidManifest.xml`（`android:networkSecurityConfig`）
  引用 `app/src/main/res/xml/network_security_config.xml`，只对 `127.0.0.1` / `localhost`
  开 `cleartextTrafficPermitted`，base-config 保持平台默认（禁止明文）。
  为什么非豁免不可：本地 ECH 网关的改写通道就是 `http://127.0.0.1:<port>`，而 localhost 的
  **隐式**明文豁免要到 Android 17（API 37）才引入；API 29–36 上不显式配置，所有改写请求会被
  OkHttp 拦成 `CLEARTEXT communication to 127.0.0.1 not permitted by network security policy`
  并回退直连 —— 在 SNI 阻断网络下等于功能全废（2026-10-02 模拟器实测）。
  刻意**不用** `android:usesCleartextTraffic="true"`：那是全局放开明文，把整机的降级空间
  一起打开，而这里只需要回环。
  失效条件＝minSdk 抬到 37（届时隐式豁免已生效，这份配置可删），或网关改写通道改为 https。
  重认日期 2027-04-02。

- **静态 CF 兜底 IP 表只有一份，放在 commonMain**（`HanimeConstants.CF_FALLBACK_IPS`）。
  两个消费方读它：jvm 的 `HanimeDns` 解析链最后一档（以及 autoBuiltInHosts 档的探测源）、
  三端起服网关时的 `ip-list` 种子（iOS 经 `EchGatePortReporter.fallbackSeedCsv()`）。
  为什么必须共用一份：iOS 走 Darwin 引擎、Kotlin 侧没有 `HanimeDns` 那套解析链，
  唯一能给网关的种子就是这张静态表；此前 iOS 传空 ⇒ 网关自己经 DoH 解析被污染
  （实测 hanime1.me → Facebook 段、拨号 15s 超时）。网关会对种子逐 IP 拨号挑能连的，
  所以给全表安全；**ip-list 一律不得传空**（Android 侧的 `echGateSeedIps()` 是"探测∪该表"）。
  失效条件＝网关对种子逐 IP 探测的行为被移除，或三端各自引入独立解析链（届时"共用一份表"
  的前提不再成立）；表内容变更后 iOS 报文随之变化，需复验。重认日期 2027-04-02。

- **换网要摘的连接池采用登记制**（`ServiceCreator.registerConnectionPoolEvictor`）：谁建池谁登记，
  复位入口不认识任何具体客户端。此前清单一字排开写死（hClient / getchuClient / downloadClient /
  CDN 链），桌面下载控制器那组独立池就漏了一整轮 —— 差集是这类清单的必然结局。
  新增自建 OkHttp 客户端时**必须登记**（守卫测试只能证"遍历生效"，证不了"每个都登记了"）。
  失效条件＝复位入口改为按类型/注解自动发现（届时登记表可删）；或引入统一的 client 工厂
  使所有客户端都出自同一处（那就不需要登记）。重认日期 2027-04-02。

## iOS 播放：本轮不接 ECH 网关（阶段 3.2 的评估结论）

- **结论：不在本轮立项实现**（明确记为"不做"）。iOS 播放继续走直连，UI 保持如实说明
  （`use_ech_gate_summary` 三语种已写明"iOS 播放仍走直连，不经网关"）；
  `PlayerWiring.ios.kt` 的 `rewriteForGate` 恒 `null`，且**不覆写** `onGateLoadOutcome`
  （不过网关就没有"网关结局"可报，编一条只会污染该域健康）。

- **为什么 `AVURLAssetHTTPHeaderFieldsKey` 不行**（既有结论，复核仍成立）：官方文档明确
  不保证对所有请求生效，HLS 分片更是不经过它。写出一个"有时生效"的改写比承认做不到更糟。

- **正解路径已探明，且比原判断更省**：`mediamp-avkit 0.5.0` 的
  `AVKitMediampPlayerFactory.create(...)` **带 per-open 钩子**
  `configurePlayerItem: (AVPlayerItem, MediaData) -> Unit`（本机从缓存
  `mediamp-avkit-iossimulatorarm64-0.5.0-metadata.jar` 的 klib 元数据核对签名与 KDoc：
  "optional per-open hook to customize each [AVPlayerItem] … before it is attached to the player"）。
  于是**不必 fork mediamp / 不必改资源加载层**：在该钩子里拿到 `item.asset as AVURLAsset`，
  设 `asset.resourceLoader.setDelegate(...)` 即可接入 `AVAssetResourceLoaderDelegate`。
  这一点修正了交接文档"需动 `:video:engine` 的 iOS 资源加载层"的估计。

- **但为什么仍不本轮做**：`AVAssetResourceLoaderDelegate` **只拦自定义 scheme**。要让 AVPlayer
  的每个子请求都进 delegate，必须把媒体 URL 换成自定义 scheme（如 `lhgate+https://…`），
  然后由我们**自建取数层**：拉 m3u8 → 把分片/音轨 URL 一并改写成自定义 scheme → 逐分片
  经 `http://127.0.0.1:<port>` + `X-Ech-Target` 头取回 → 处理 `AVAssetResourceLoadingRequest`
  的 content-information（长度/类型）与 byte-range，还要覆盖 HLS 变体播放列表。
  这是一个独立子项目（播放/下载两类内容、三种 track），且需要 macOS/Xcode + 真机验证 ——
  当前环境（Windows，仅能交叉编译 iOS klib）无法实跑，做出来也无法证明它"真的通了"。

- **保留的将来落点**：立项时从 `MediampAvPlaybackEngine` 构造处的 `configurePlayerItem`
  钩子接入，取数层复用 `:shared` 的 `PlayerNetworkConfig`（`rewriteForGate` / `proxyUrlFor`），
  与 HTTP/其它端同一份出口判定；失败必须能回退原生直连（自定义 scheme 取数失败时换回 https）。
  失效条件＝mediamp-avkit 移除 `configurePlayerItem` 钩子（届时需 fork 或换引擎），
  或 iOS 获得可直接注入按请求头的官方 API。重认日期 2027-04-02。
