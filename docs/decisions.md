# 决策记录

> 本文件当前保存 2026-10-02 起的条目。更早的条目仍完整留在 git 历史里
> （`git checkout HEAD -- docs/decisions.md` 可取回并合并）。

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
