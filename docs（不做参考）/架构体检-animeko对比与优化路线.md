# 架构体检：LoveHan1me × animeko 对比与优化路线

> 结论来源：仅通读两侧源码（未参考 docs/ 与 git 历史）。
> 对比基准：`E:\hanime1\references\animeko-main\animeko-main`（OpenAni 团队项目）。
> 生成日期：2026-09-17

---

## 0. 一句话结论

**LoveHan1me 的“包级架构”是健康的，问题不在“该不该拆成 20 个 Gradle 模块”，而在 ① 单模块内的三个上帝包/上帝文件、② 测试只能跑在 desktopTest、③ 桌面端仍是“能跑的手机 App”、④ 一批已建但未接线的半成品契约。**

animeko 那套 60 模块的拆分是 **10+ 人协作 + 编译速度** 驱动的产物，对当前单人/小团队维护体量是净负债。**不要照搬模块拆分，要照搬它的 5 个机制**（见 §2.1）。

---

## 1. 规模与结构对照

| 维度 | LoveHan1me | animeko | 判断 |
|---|---|---|---|
| Gradle 模块数 | 3（`:app` `:shared` `:desktopApp`） | ~60 | 数量级差异，但**不可直接对齐** |
| shared / 主模块 kt 数 | `commonMain` **393**（src 合计 610） | `app-data` 523、`ui-foundation` 277、`client` 190…被摊到 40+ 模块 | 复杂度相当，**组织方式不同** |
| 最大手写文件 | `Parser.kt` 1217、`AdvancedSearchSheet.kt` 1082、`VideoPlayerControls.kt` 1025、`VideoRouteHostScreen.kt` 1021、`VideoPlayerUi.kt` 883、`NetworkSettingsScreen.kt` 815 | 最大 `EpisodePage.kt` 1350、`EpisodeViewModel.kt` 1244、`SubjectDetailsPage.kt` 1219；**>1000 行仅 13 个，>500 行 95 个** | **我们是 6 个 >800 行挤在一个模块里**，它是 13 个散在 40 模块里 → 单文件水位接近，但集中度差 3 倍 |
| 分层 | feature 分包 + data 按技术分层，**无 domain/use case** | data/domain/usecase 完整（`UseCaseModules.kt` 27 处定义） | 缺 UseCase 层，但**不宜全面补**（见 §2.4） |
| DI | 无 Koin，手工 `object` 单例 + provider 注册 + `AppViewModelStore` | Koin 3.5.6（`CommonKoinModule.kt` 661 行 + 平台扩展） | 可手工维持，**但必须解决可测试性**（§2.2） |
| 导航 | Navigation 3 + 自研 `TopLevelBackStack`，22 主路由 + 14 设置子路由，35 条 `entry<>` | Navigation 3，单点 `sealed class NavRoutes : NavKey` | **基本对齐**，我们甚至更细 |
| 测试 | **无 commonTest**；`desktopTest` 24 + `iosTest` 2；`:app` 仅 1 个模板 androidTest | 测试 kt **341**，源集 40 个，`commonTest` 219 | **最大短板**，差一个数量级 |
| 静态检查 | 无 detekt/ktlint/spotless/**.editorconfig** | 无 detekt/ktlint，但有 44KB `.editorconfig`（`max_line_length=100`） | 低成本可补 |
| 构建性能 | `org.gradle.parallel` **被注释**，无 `caching=true` / `configuration-cache` | `org.gradle.caching=true` + `configuration-cache=true` + develocity buildScan | **白捡的性能，今天就该开** |
| CI | 2 job（Windows 编译+测试 / macOS-14 编译 iOS），iOS XCUITest **未接入** | 由 `src.main.kts`(2347 行) 生成 `build.yml`(1953 行)，6 构建 job + 7 验证 job，关键步骤 3 次重试 | 可补，但优先级低 |

---

## 2. 结构优化建议

### 2.1 该照搬的 5 个 animeko 机制（高 ROI）

1. **`AbstractViewModel` 基类** — `ui-foundation/.../AbstractViewModel.kt`
   `RememberObserver` 引用计数 + 可注入 `CoroutineContext`（`StandardTestDispatcher` 让 VM 后台协程跑虚拟时间）。
   一套基类同时解决「桌面没有 androidx ViewModel 生命周期」和「VM 可确定性测试」。
   → 落点：新增 `core/ui/AbstractViewModel.kt`，让 `feature/*/*ViewModel` 收敛继承。

2. **`ui-testing` / `ui-preview` 独立共享设施**
   animeko 的 `utils:ui-testing`（`runAniComposeUiTest` expect/actual + 截图断言）被 15+ 模块 `implementation` 依赖。
   → 我们对应的是 `feature/preview/ComposePreviewDataSource.kt`（目前只是假数据）。把它升级为「预览数据源 + UI 测试脚手架」共用设施。

3. **`.editorconfig` 统一格式**（`max_line_length=100`）
   零依赖、零构建成本，立刻消除跨会话的代码风格漂移。

4. **路由单点 sealed class + 全 `@Serializable`**
   我们已有 `HanimeScreen.kt`（22 个 `@Serializable` 路由键），**已经对齐**，无需改动。
   差的是 animeko 有 `AniNavigatorTest.kt` 这种对导航执行器的单测——配合 §2.2 一起补。

5. **构建开关**：`org.gradle.caching=true`、`configuration-cache=true`、放开 `parallel`。
   animeko 靠这个把 60 模块的编译时间压下来；我们 3 模块但 393 个 commonMain 文件，同样吃收益。

---

### 2.2 P0：把测试从 desktopTest 提到 commonTest（最大短板）

现状：`Parser.kt`（1217 行，ksoup 解析，pure Kotlin）住在 `commonMain`，但测试写在 `desktopTest` → **iOS/Android 侧的逻辑永远不被测试覆盖**，且 CI 只能靠 Windows Job 跑。

animeko 的做法是把纯逻辑测试全放 `commonTest`（219 个文件）。

行动：
- 新建 `shared/src/commonTest`，把 `desktopTest` 里的纯逻辑测试（GIF 编码器、MedianCut 调色板、文件名反解、搜索筛选持久化、`Parser` 解析）迁进去。
- 只保留**确实需要 JVM/Skia 原生库**的（如 `decodeAvatarSource`）在 `desktopTest`。
- 顺带修掉一个设计坏味：`SettingsRepository` 是「只允许 install 一次」的全局可变单例，导致多个测试要用 `runCatching { SettingsRepository.install(...) }` 绕过 `check`（`JavchuHomePageParseTest.kt:61`）。改成可注入实例后测试会干净很多。

---

### 2.3 P0：拆掉三个上帝包 / 上帝文件（在 `:shared` 内部做，不动 Gradle 模块）

不要拆 Gradle 模块，但**必须拆包**：

| 现状 | 问题 | 建议切法 |
|---|---|---|
| `feature/video/` 29 文件，塞了详情页 + 播放器 UI + 评论 + 简介 + 路由宿主 | 播放器上帝包 | `feature/video/detail/`（简介/评论）、`feature/video/player/`（TopBar/BottomBar/CenterControls/Controls/Ui/StateCards） |
| `site/hanime1/Parser.kt` 1217 行 | 单一巨型解析器 | 按页面切 `parser/home.kt` `parser/search.kt` `parser/detail.kt` `parser/comment.kt`，`Parser.kt` 只留入口聚合 |
| `AdvancedSearchSheet.kt` 1082 行 | UI 与筛选状态耦合 | 拆 `AdvancedSearchSheet`（壳）+ `filter/GenreFilter` `TagFilter` `DurationFilter` `BrandFilter` |

`VideoPlayerControls.kt`(1025) / `VideoRouteHostScreen.kt`(1021) 这两个播放器文件**先别动**——第二轮 animeko 复刻正在改它们（P0–P8），等复刻定稿后再按新结构切，避免与进行中的改动打架。

---

### 2.4 P1：UseCase 层——只在两个域引入，不要全面铺

animeko 有完整 `domain/usecase`（27 处 Koin 定义）。我们完全没有，UI 直接调数据层：
- `feature/login/FormLoginScreen.kt:87` — Composable 里直接 `NetworkRepo.login(...).collect{}`
- `feature/video/VideoRouteHostScreen.kt:419,511` — Composable 里直接 `DatabaseRepo.WatchHistory.updateProgress(...)`

**建议：不要全面补 UseCase**（违反「可改动性 > 完整性」，且会把 393 个文件全搅一遍）。只在最复杂的两个域引入：
1. `feature/video`（播放进度同步 + 收藏/稍后再看 + 历史写入，逻辑最纠缠）
2. `feature/search`（筛选快照 / 历史 / 预设三套状态同步）

其余保持 ViewModel → Repo 直连，等它自然变复杂再抽。

---

### 2.5 P1：清理已建但未接线的「空转契约」

这是当前**最该止血**的一类。半成品契约比没有更糟——它让人误以为能力已存在。

| 契约 | 现状 | 处置 |
|---|---|---|
| `site/SiteCatalog.kt` + `SiteSwitcher.kt` | 注释明写「现阶段仅作契约落点，不接管现有 `HanimeConstants`」→ **网络层根本没消费它** | 二选一：让 `HanimeNetwork` 真正读 `SiteCatalog`，或**删掉**。别留着 |
| `feature/player/PlaceholderPlaybackEngine.kt` | desktop/iOS 返回占位引擎，标「P5-2 接真引擎」 | 排期接真引擎（这是桌面/iOS 能用的前提），或明确标注为实验端 |
| `VideoPlayerBottomBar.kt` `KazumiDanmakuField` | 明写「没有弹幕后端，发送只提示暂未开放」 | **直接删掉占位 UI**（弹幕无后端，属废案） |
| iOS 亮度/全屏手势 | `VideoPlayerUi.kt` 注释「iOS 尚未实现 → false 时隐藏入口」，但**左半屏竖滑照样弹 HUD 百分比** | 手势反馈与实际效果不一致 = 假反馈，要么补实现要么连同 HUD 一起隐藏 |
| `PlatformSettingsRoutes.kt` / `HomeSettingsRoute.kt` 占位 | 参数回显式占位页 | 列清单逐个补或移除 |
| `DownloadWorkController` iOS | NoOp | 明确标注 iOS 不支持下载，别让 UI 显示入口 |

---

### 2.6 P2：工程一致性收尾

- **compileSdk 双轨**：`shared` 走 `han1me.android.compileSdk=36`，`:app` 硬编码 `37`（`app/build.gradle.kts:18`）→ CI 要装两套平台。统一。
- **buildSrc 与 build-logic 并存**：`buildSrc/src/main/kotlin/Config.kt` 只为一个 `Config.thisYear` 服务，合进 build-logic 后删掉 buildSrc（它还会拖慢配置期）。
- **Coil 双版本**：`libs.versions.toml` 里 Coil 3.6.1（KMP，shared 用）与 2.7.0（`:app` 用）并存，且 `:app` 的 `HImageMeower.kt:21-28` 自建 `OkHttpClient + HanimeDns + Coil2 ImageLoader` → **两套图片库 = 双份内存缓存 + 两套网络栈**。随 `:app` 纯壳化一并删。
- **`:app` 收尾**：目前 27 kt / 0 Fragment / 0 自定义 View / 0 layout XML，已接近纯壳。残留：`HanimeCacheManager.kt:44` 仍用 kotlinx.serialization 做 `HanimeVideo` JSON 落盘、`HImageMeower`、`app/build.gradle.kts:150` 的 okhttp。清完即可摘掉「迁移期」帽子。
- **源集层级重复声明**：`han1me-kmp-library.gradle.kts:74-84` 里 `group("android")` 同时出现在 `group("jvm")` 内部和 `common` 下，两处重复。核实后删掉冗余的那处。

---

## 3. 功能打磨清单（已有能力 → 深度补齐）

按 **ROI 从高到低**，都不需要动架构：

| # | 项 | 现状 | 对标 animeko | 成本 |
|---|---|---|---|---|
| 1 | **骨架屏 / shimmer** | 全库无 skeleton，只有 `LoadingIndicator` + 趣味文案 | `thirdparty/placeholder/`（Placeholder/PlaceholderHighlight） | 低 |
| 2 | **进度条帧预览缩略图** | 只有时间文字气泡 | `progress/MediaProgressFramePreview.kt` | **低——mediamp 已有 `getPreviewFrame()`，是"有能力没接线"** |
| 3 | **无障碍 contentDescription** | 覆盖率 <10%，播放器 `Icon` 全写 `null` | 全面 `contentDescription` + `testTag` 语义化 | 低 |
| 4 | **桌面端三件套** | 只有 `Window` + `exitApplication`：**无托盘、无菜单栏、无窗口状态记忆** | `AniDesktop.kt` + `WindowStateRepository` + `Tray.kt`（最小化到托盘）+ 三套自定义标题栏 | 中 |
| 5 | **更新落地** | 有 `AppUpdateChecker`（远端 JSON / 忽略版本 / 强制更新 / 离线缓存），**但无下载安装** | `FileDownloader.kt` + `UpdateInstaller.kt` + `UpdateNotifierHost` | 中 |
| 6 | **自动连播下一集** | 有手动 `onNextClick` 按钮，无自动 | `SwitchNextEpisodeExtension.kt`（距结尾 <5s 自动切） | 低 |
| 7 | **字幕轨 / 音轨** | `Parser.kt` 已解析站点字幕文本字段，但播放器无字幕轨、无音轨 | `SubtitleSwitcher.kt` + 外挂 ass、`AudioSwitcher.kt` | 中（先确认站点字幕可用性和格式） |
| 8 | **边下边播** | ❌ 仅下载完成后播放 | `CacheOnBtPlayExtension.kt` + piece 优先级调度 | 高（BT 场景，HTTP 直链意义有限） |
| 9 | **桌面全局快捷键** | 仅播放器内 `onKeyEvent` | `PlayerKeyboardShortcuts.kt`（空格/F/←→/↑↓/B/I/数字键倍速）+ 全局注册 | 中 |
| 10 | **空态/错误态细化** | 有 `EmptyContent`/`ErrorContent`/`PageLoadingState` | 同左 + `LoadErrorCard`（数据源级刷新/重启） | 低 |

---

## 4. 功能新增清单（按 ROI 排序）

### 高 ROI（建议做）
1. **骨架屏**（§3-1）
2. **帧预览缩略图**（§3-2）— 引擎能力现成，纯接线
3. **桌面托盘 + 窗口状态记忆**（§3-4）— 桌面端目前是"能跑的手机 App"，这是从"能用"到"像桌面软件"的分水岭
4. **自动连播 + 播放列表连播**（§3-6）
5. **无障碍补齐**（§3-3）— 顺带为后续 UI 测试铺路
6. **更新下载安装闭环**（§3-5）
7. **图片查看器**：animeko `ui-foundation/.../imageviewer/`。我们的账号/详情页有多图场景，值得做

### 中 ROI（看排期）
8. **字幕轨**（需先确认站点字幕真实可用，别为不存在的数据做 UI）
9. **桌面全局快捷键 + 菜单栏**
10. **本地视频库增强**：`importDownloaded()` 已有，可补封面/时长/分组/排序/播放列表
11. **下载任务管理页增强**：分组折叠已有，可补按状态筛选、批量操作、失败原因展示

### 明确**不建议**做（照搬 animeko 会踩的坑）
- ❌ **弹幕系统**：无后端，`KazumiDanmakuField` 是废案 → 删占位即可
- ❌ **BT / 数据源插件平台**（`MediaSource` SPI + `META-INF/services`）：animeko 的多源可插拔是「BT 站聚合」的产物。hanime1.me 是**单一在线站**，多站点目前只有 hanime1 + getchu 两个契约且未接线 → 先把 `SiteCatalog` 用起来，别上 SPI
- ❌ **Bangumi 同步 / 冲突合并**：无对应生态
- ❌ **60 模块拆分**：编译提速的收益低于维护成本，见 §0
- ❌ **放送时间表 / 评分体系**：站点数据结构不支持

---

## 5. 风险与技术债提示

1. **版本过于激进**：Kotlin 2.4.10 / AGP 9.2.1 / CMP 1.12.0 / **material3 1.12.0-alpha03**。
   `shared/build.gradle.kts:34-41` 需要显式 `-opt-in=ExperimentalMaterial3ExpressiveApi` 才能编译，说明踩在 alpha API 上。
   animeko 用的是稳定线。alpha material3 意味着 API 可能破坏性变更——**这是比任何架构问题都现实的风险**。建议评估降级到 material3 稳定版，或至少锁死版本并在 CI 加编译门禁。
2. **无静态检查 + 无 commonTest + iOS XCUITest 未入 CI** = 三端回归全靠手工。
   `iosAppUITests/NoticeAndBackupUITests.swift` 已经写好了（首启弹窗链 + 备份入口），却没接进 CI，等于白写。
3. **`build/reports/problems/problems-report.html`** 里已有一批 WARNING（废弃 `Instant` typealias、冗余转换、`monthNumber` 废弃等），趁早清，别滚雪球。
4. **双轨资源**：`:app` 的 `R` 与 shared 的 `composeRes` 并存（`shared/build.gradle.kts:204` 注释「P6 收敛」）。两套字符串 = 改文案要动两处，是已知的隐形分叉源。

---

## 6. 建议执行顺序

```
阶段 A（本周，零风险）     开 build cache / configuration-cache / parallel
                          加 .editorconfig
                          统一 compileSdk
                          删弹幕占位 UI、修 iOS 亮度假 HUD

阶段 B（结构）             建 commonTest，迁 desktopTest 纯逻辑测试
                          拆 feature/video 与 Parser.kt（避开播放器复刻期的文件）
                          AbstractViewModel 基类

阶段 C（体验）             骨架屏
                          帧预览缩略图（接线 mediamp getPreviewFrame）
                          无障碍 contentDescription 补齐
                          自动连播

阶段 D（桌面端）           托盘 + 窗口状态记忆 + 菜单栏
                          接入 iOS XCUITest 到 CI

阶段 E（收尾）             :app 纯壳化收尾（删 Coil2 / HImageMeower / okhttp）
                          SiteCatalog 二选一（接线或删除）
                          删 buildSrc
```

**不要做的事**：在阶段 B 完成前启动 Gradle 模块拆分，也不要在播放器 animeko 复刻（P0–P8）进行中动 `feature/video` 的播放器文件。
