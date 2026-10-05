# 交接：下一任 Agent 从这里接手

> 写于 2026-10-05 01:00，上一任因额度中断。
> **本文是可独立执行的手册**，不依赖任何对话上下文。先读本文，再读
> `docs/plan/后期攻坚-网络与全端-审阅与规划.md`（审阅结论 + 五阶段规划，第 10 节是执行记录）。
>
> 一条贯穿全文的纪律：**不要相信文档与提交信息，回到代码核对**。
> 本项目文档写得比代码好，已发现多处文档漂移（见第 6 节）。本文标注的
> `文件:行号` 均经核对，但改过的文件请自己再确认一次。

---

## 0. 一句话复制给下一任

```
你是 LoveHan1me 的接手 Agent。先读 E:\LoveHan1me\docs\plan\交接-下一任Agent.md，
再读同目录的 后期攻坚-网络与全端-审阅与规划.md。项目是 Kotlin Multiplatform
（Android/桌面/iOS）的 hanime1.me 第三方客户端，当前在后期攻坚，主线是
「网络连接」与「全端体验打磨」两条。不要相信文档与提交信息，回到代码核对。
接下来按交接文档第 3 节的未完成清单继续，每个改动都要本机实跑编译与测试验证。
```

---

## 1. 项目是什么

- 跨平台（Android / 桌面 / iOS）hanime1.me 第三方客户端，KMP + Compose Multiplatform。
- 定位看 `POSITIONING.md`：**三端都能用 > 四块都不碍事 > 同一个人做同一件事用我们更顺**。
  下层没达成不许宣称上层。
- 模块：`:app`（Android 壳）、`:shared`（业务主体）、`:desktopApp`（桌面壳）、
  `:video:{contract,engine,ui,surface}`（播放四层）、`echgate/`（Go 网关）、`iosApp/`。
- 网络主线：本地 ECH 网关（`echgate`，监听 `127.0.0.1:<port>`）+ 调度器
  `EgressScheduler` 按域排出口（Gate / 代理 / 直连），受限网络下靠它免梯直连。

---

## 2. 已完成（本轮，全部经本机编译/测试验证）

| 项 | 落点 | 验证 |
|---|---|---|
| iOS 网络页去占位化 | `shared/src/iosMain/.../app/navigation/settings/PlatformSettingsRoutes.ios.kt`（整文件重写） | iOS 编译通过 |
| 三态判定上提 commonMain（jvm/iOS 共用） | 新文件 `shared/src/commonMain/.../data/network/egress/EgressStatus.kt` | desktopTest 全绿 |
| 三态判定首获测试覆盖 | 新文件 `shared/src/commonTest/.../egress/EgressStatusTest.kt`（10 用例） | 全绿 |
| iOS 播放 UA 空串 → 真值 | `shared/src/iosMain/.../data/network/PlayerWiring.ios.kt` | iOS 编译通过 |
| `use_ech_gate_summary` 三语种改为实情 | `shared/src/commonMain/composeResources/values{, -zh-rCN, -zh-rTW}/strings.xml` | 编译通过 |
| `budgetFor` 删掉从未被读的 `route` 参数 | `SchedulerModels.kt` / `EgressScheduler.kt`（2 处）/ `EgressSchedulerTest.kt`（5 处） | desktopTest 全绿 |
| `RouteRegistry` 并发丢更新修复 | `shared/src/commonMain/.../egress/RouteRegistry.kt`（改用 `PlatformLock`） | desktopTest 全绿 |
| 三处过期注释修正 | `EchGate.kt:122`、`EchGate.ios.kt:46`（"iOS 无网关运行时"） | 编译通过 |
| 受限网模拟器脚本（**未执行**） | `tools/phase5/restrict_sim.sh` + `tools/phase5/fake_proxy.py` | 未跑（需 macOS + sudo + 设备） |

测试基线：**`:shared:desktopTest` 472 用例 / 1 失败**（失败项见第 5 节）。

---

## 3. 未完成清单（按建议顺序，每条都给了改动落点）

### 3.1 阶段 1｜让多步让位真的跑起来 ← **最高优先**

**问题**：调度器排出的计划里，非网关步失败后**直接抛出**，不再让位给后续步。
于是"attempts 全部耗尽"这个语义只在网关步之间成立，任何排在非网关步之后的路由都是死步。

**落点**（两处执行器，改法一致）：
- `shared/src/jvmMain/.../data/network/interceptor/EchGateInterceptor.kt:114-144`
  第 130 行 `throw e` 是症结：要么成功 `return`，要么 `throw`，永不进入下一步。
- `shared/src/commonMain/.../data/network/EchGatePlugin.kt:131-158`（Darwin 侧同构，`:144`）

**改法**：非网关步失败改为 `continue` 到下一步（`Continue` 而非 `throw`）。
非幂等方法禁止让位——现已有 `isIdempotent`（`EgressTypes.kt:123`）判定，沿用。
循环走完仍无出路时，保持现有的 `NoRouteException` 兜底（`:161` / 插件 `:170`）。

**验收（必须新增 e2e，现有 13 条用例一条都没覆盖这块）**：
```
Gate 抛 IOException + Default/代理 成功 → 期望 2 次请求，返回 200
Gate 成功                                → 期望 1 次请求
Gate 失败 + 后续步也失败                 → 期望 NoRouteException
```
反向验证：撤掉修复，三条应转红。

### 3.2 阶段 0.1｜路由折叠（`RouteId.Default`）

**问题**：`UserProxy / SystemProxy / Direct` 三者在执行器里**物理等价**——
JVM 侧都是 `chain.proceed(request)`，出口由 client 级 `HanimeProxySelector` 决定
（`ServiceCreator.kt:158/134/116`）；Darwin 侧都是 `proceed(request)`，手填代理根本不生效。
后果：记账把"走代理成功"记到 Direct 头上；iOS 上手填代理用户连续 3 次"成功"（其实是直连）
→ `RouteHealth.LOCK_SUCCESS_THRESHOLD` 粘滞锁定 → **网关被挤出首位**。

**⚠️ 上一任的修正（务必遵守）**：桌面 `PlayerWiring.desktop.kt:60-67` 里
`UserProxy → resolveMediaProxyUrl() ?: tunnelUrl`、`SystemProxy → tunnelUrl` 的分派
是**真的**有意义的（SOCKS 解析为 null 时退隧道），折叠时**必须保住这条语义**，
不能一刀切成 `Default`。审阅时我漏说了这点。

**建议**：新增 `RouteId.Default`，HTTP 层三路由合一（计划变 `[Gate, Default]`）；
播放层保留细分。诊断里 `Default` 显示成"当前网络出口"，不谎称"直连"。

### 3.3 阶段 2｜受限网验收

`tools/phase5/restrict_sim.sh` 已写好但**没跑过**。它需要 macOS + sudo（改 `/etc/hosts`）+ 设备。
先在目标机跑 `restrict_sim.sh status` 确认行为，再串 `smoke_desktop.sh` / `smoke_android.sh`
把 R2 / R4 / R5 跑掉。R1（真 SNI 阻断）、R3（真 CF 挑战）、R6（三端真机）仍需真受限网。

### 3.4 阶段 3.2｜iOS 播放进网关

`PlayerWiring.ios.kt` 的 `rewriteForGate` 恒 null（AVPlayer 无可靠按请求注入头）。
正解是 `AVAssetResourceLoaderDelegate` 接管取数，需动 `:video:engine` 的 iOS 资源加载层。
先评估、立项或明确记"不做"到 `docs/decisions.md`；在此之前 UI 必须明说（已做）。

### 3.5 阶段 4｜播放出口补回退与上报

- Android `EchGateDataSource.kt:31-42` 无回退（桌面 `DesktopMpvPlaybackEngine.kt:136-145` 有
  `allowGate=false` 回退一次）→ 三端在这里分叉，补齐。
- 播放 outcomes 不上报 → 视频域健康靠图床推断，视频 CDN 与图床不同域时**永不熔断**。
  需给 `PlayerNetworkConfig` 加 `onLoadOutcome` 钩子回 `EgressReporter`。
- `PlayerWiring.android.kt:30` 那句"系统属性是否被 Exo 尊重**未实测**"——实测掉，别留着。

### 3.6 阶段 4.3｜能力矩阵守卫测试

把三端 `supportsFullscreen` / `supportsBrightness` / `shouldEnterPip` /
`rewriteForGate != null` / `proxyUrlFor != null` / `userAgent.isNotBlank()`
钉成期望表。这是防"假动作"回归最便宜的手段（iOS `togglePlayPause` 空实现、
`VideoPageHost.supportsFullscreen` 默认 `true` 而 `supportsBrightness` 默认 `false`，
默认值方向不一致，都该是 `false`）。

---

## 4. 环境事实（省得你重新踩）

### 构建与测试

```powershell
$env:JAVA_HOME='D:\DevCache\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2'
Set-Location 'E:\LoveHan1me'
& .\gradlew.bat :shared:compileKotlinDesktop              # 离线 14s / 增量 ~35s
& .\gradlew.bat :shared:compileKotlinIosSimulatorArm64    # 1m38s，无需 Xcode
& .\gradlew.bat :shared:desktopTest                       # ~1m50s
```

- 系统 `JAVA_HOME` 是 JDK 17，**必须**手动指向上面那个 JDK 21（Gradle wrapper 是 9.4.1）。
- 首次跑 iOS 目标要联网拉 klib（`--offline` 会失败），之后可离线。
- **iOS 编译本机可用**（Kotlin/Native 交叉编译），别再假设只能靠 CI。
- Android SDK 在 `D:\DevCache\Android\Sdk`；没连设备，Android 只能编译不能跑。

### 两个已经踩过的坑

1. **iosMain 的 `Res.string.xxx` 必须逐个 import**。
   生成的访问器是 `lovehan1me` 包下的**扩展属性**，只 `import lovehan1me.Res` 不够，
   iOS 侧会全部报 "Unresolved reference"。既有约定见 `MainViewController.kt:21`。
   JVM 侧不需要。
2. **PowerShell 工具的 stdout 经常拿不到**——要 `Set-Content` 写文件再用 Read 读。
   Bash shim 缺 coreutils（`ls`/`rm`/`head` 都没有），别用。

### git 事故（重要）

- 执行期间 `.git/refs` 被**外部 git maintenance 进程删了两次**
  （伴随 `.git/objects/maintenance.lock`、`shallow.lock`）。
- 已用 reflog tip `a81bb3d` 恢复 `refs/heads/main` 与 `refs/remotes/origin/main`
  并重建 `refs/{heads,tags,remotes}` 目录树。若又坏了，照此修：`git log` 会失效，
  HEAD 内容见 `.git/HEAD`，最新 tip 从 `.git/logs/HEAD` 末行取第二个哈希。
- 对象库**有缺失**（`git stash` 报 `invalid object ... for '.github/workflows/ci.yml'`）。
  建议有网时跑 `git fsck` / `git fetch --all` 复核。
- **在本仓库慎用 `git stash`**。

---

## 5. 当前唯一的失败测试（别当成噪声）

`EchGateLiveTest.视频CDN经网关可建立连接` 失败，同轮输出：

```
LIVE site code=200 server=cloudflare      ← 网关→站点通
LIVE video-cdn code=502 server=null       ← 网关→ vdownload.hembed.com 上游建连失败
```

这是**真缺陷线索**：桌面端"能浏览、视频 CDN 经网关不通"，与 iOS 播放不过网关是
同一类"能看不能播"的两种表现。本次改动未碰网关出站与 CDN 解析，判断为既存，
但**未做基线复跑**（git 损坏期间不敢再动）。接手后建议先补一次基线确认。

---

## 6. 已确认的文档漂移（别照抄文档，看代码）

| 文档说 | 代码实际 |
|---|---|
| Phase 2 已把就绪等待收窄为"仅首步是 Gate 且 Starting" | `EchGateInterceptor.kt:86` **无条件**调用 `awaitReadyIfStarting()`，且它带自愈拉起副作用（`EchGateRuntime.kt:216-223`） |
| "iOS 无运行时旧注已删" | 已在本次修掉（`EchGate.ios.kt:46`、`EchGate.kt:122`） |
| "purpose × route 预算表" | `budgetFor` 只有 purpose 一维（本次已删死参数） |
| "显式代理缺口属 Darwin 平台限制，不硬做" | 不是"不排"，是排了却执行不了还记错账（见 3.2） |

---

## 7. 红线（POSITIONING.md，不可松动）

- **不碰用户数据**：不收集、不上传任何使用行为与播放记录。没有服务端。
- **不经手内容**：不提供/托管/分发媒体内容，流量不经中转服务器。
- **不商业化**：无付费、广告、推广；上游不接受公开宣传。
- **诚实原则**：UI 上显示的每个字段，都要能回答"这是引擎的真值，还是我们替它编的"。
  编的就改掉。平台做不到的明说，用 `supportsXxx()` 门控，不做"假动作"。
- **冲突当场问**：发现文档与代码打架，问用户，别自己猜文档还作不作数。

## 8. 验收口径（改完怎么算完）

- 每项独立提交；改 `:shared` 后按 CI 两个 job 口径实跑，**如实报告用例数与失败数**。
- 守卫测试要做反向验证（撤掉修复应转红）。
- 平台相关项必须注明"模拟器已验 / 待真机"，**不把模拟器结论当真机结论**。
- 不写没核实的结论；涉及外部世界（站点、第三方库）的论断进 `docs/evidence/`。
