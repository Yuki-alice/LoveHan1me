# LoveHan1me 后期攻坚：最终分析与规划（收口版）

> **状态：分析 + 规划已完成，编码施工已暂停（2026-10-07 01:31）。**
> 全部结论取自**源码与命令输出**；未采用 `README` / `POSITIONING.md` / `docs/` 既有说明 / git 历史等
> AI 生成材料（按本次铁律一律不予采信）。
> 入口文档：本文件（总纲）→ `docs/plan/late-stage-hardening.md`（唯一活计划，含逐步施工规格）。

---

## 1. 一句话判定

**底层工业合格，表层治理欠债。**
网络 / 数据 / 并发已达准工业水准；真正拉开与顶级客户端差距的是 UI 层的 Compose 素养、
状态治理，以及**一整套度量与质量的基建缺席**。

最危险的从来不是"有 bug"，而是：**测试基线本身不可信，导致无法证明任何优化是变好还是变坏。**
这一条已在本轮解决（见 §4）。

---

## 2. 事实基线（可复核）

### 2.1 规模（主理人实测）

| 模块 | 文件数 | 行数 |
|---|---:|---:|
| `shared/commonMain` | 406 | 60,303 |
| `video/ui` | 77 | 10,173 |
| `shared/androidMain` | 62 | 4,852 |
| `video/engine` | 49 | 4,141 |
| `shared/iosMain` | 62 | 3,352 |
| `shared/desktopMain` | 44 | 3,142 |
| `video/contract` | 21 | 2,469 |
| `app` + `desktopApp` 外壳 | 10 | 1,453 |

单文件 TOP5：`site/hanime1/Parser.kt` 1251 · `feature/video/VideoRouteHostScreen.kt` 1115 ·
`feature/search/AdvancedSearchSheet.kt` 1082 · `feature/settings/NetworkSettingsScreen.kt` 967 ·
`data/NetworkRepo.kt` 835。**全仓 TODO/FIXME 仅 3 处** —— 代码表面极整洁，问题不会自曝。

### 2.2 六维评分（架构侧）

| 维度 | 分 | 依据 |
|---|---:|---|
| 数据层 | 4 | 网络出口判据收敛到单一实现 |
| 并发 | 4 | 自有代码 `GlobalScope` 0 处、裸 Thread 0 处，`runBlocking` 仅 2 处且理由成立 |
| 平台治理 | 4 | 83 个 expect，**全平台 0 个 `TODO()`** |
| 分层 | 3 | 有真护栏（`ModuleLayeringTest`）；但 `settings.gradle.kts` 里"ui 禁 mediamp"的说法与实情不符 |
| 状态管理 | 3 | 存在 1115 / 1082 / 967 行的 UI 巨物 |
| Compose 性能 | 3 | 移植播放器有 40+ 处 `@Stable`/`@Immutable`，**自研 6 万行只有 2 处** |

**核心命题**：把移植代码里已经成立的工程纪律，铺开到自研代码 —— **不是重写任何一层**。

### 2.3 测试真值（QA 侧）

- 103 个测试文件、695 个 `@Test`；**零测试**模块：`Parser.kt`、`NetworkRepo.kt`、自研
  `TopLevelBackStack.kt`、`video/surface`、`desktopApp`、`echgate`、三大 UI 巨物。
- 无 `@Ignore`/assume 滥用，但有 **≥18 个用例靠 `println + return` 静默绿**；其中覆盖 `Parser` 的
  9 个用例依赖 gitignore 的 `.workbuddy/` 夹具 → **CI 干净 checkout 上必然跳过（等价零覆盖）**。
- 3 个零断言用例，其中 2 个还在 CI 上真发网络请求。
- CI 本身不偷懒（无 `-x test`、无 `continue-on-error`），但 **`iosTest`（8 用例）源集从不执行**。
- 基建全缺：**LeakCanary / StrictMode / BaselineProfile / benchmark / APM 均没有**。

### 2.4 产品完成度（产品侧）

- 水位：工程质量不低（播放器/下载/设置强于多数开源同类），但产品形态仍是**"网站镜像"**，
  未成为内容平台。还原出 **~37 个可路由屏幕**，多数 A/B 档。
- 假数据仅存在于 `@Preview` 数据源，**CMS 不是 mock**。
- 产品侧 P0：首页无推荐 Feed（一次性拉整页分类块，`HomePageContent.kt:109-186`）；
  搜索无联想/无热搜（全仓零命中）。
- ⚠️ 甄别：视频卡长按菜单在桌面/iOS 是空函数体（`VideoCardItem.kt:344`），但属**代码显式登记
  且有单测钉住**的已知取舍，**不是 bug，不可盲修**。

---

## 3. 最硬的交叉验证

工程师（实机跑）与 QA（XML 取证）互不知情地指向同一事实：

> 桌面套件 684 用例，**5 次全量 4 次红**，每次红的不是同一个；唯一恒失败者是
> `EchGateLiveTest`（27s，套件最慢）。而 **CI 上它因缺 echgate 产物走 SKIP 分支所以是绿的**。
> 即：**环境越全就越红，环境越残缺反而越绿。**

这不是某个用例写坏了，而是**测试真实性与执行环境绑定反了**。修复它是一切优化的前置条件。

---

## 4. 已完成的三项施工（均已反向验证）

| 任务 | 内容 | 证据 |
|---|---|---|
| **阶段 0** | live 用例加 `HAN1ME_ECHGATE_LIVE` 环境变量门禁 | 未设变量 → 4 个用例记 `skipped`（用 JUnit4 `Assume`，**不是** `println+return`，否则 XML 算 PASSED、看不出门禁生不生效）；主理人独立复跑 `--rerun-tasks` 核实 |
| **A1** | 图片加载器**进程级单例化** | `HanimeImageLoader.jvm.kt` / `.ios.kt` 改为复用同一 `loaderLock` + `singletonLoader`，`remember` 只缓存结果 → 8 个调用点零改动；新增 5 条守卫用例，**3 条反向验证转红** |
| **C** | 搜索分页并发控制 | 新增 `core/domain/state/PagingGate.kt`（判重 + 请求标识 + restart）；`SearchScreen.kt:441` 的 `{ viewModel.page++; executeSearch() }` → 单次 `loadNextPage()`，分页序号归 VM；**反向验证打红 2/6/1 条** |
| **A1.5** | 桌面 Coil 单例**出口纠正** | `Main.kt` 的单例由 API 出口改为委派共享的 CDN 出口；行为守卫钉 `createCdnFetchClient().cookieJar === CookieJar.NO_COOKIES` + 配置不变式守卫；RV-A/RV-B 转红 |

**测试基线演进**：531 → 536(A1) → 544(C) → **546** —— `failures` 恒 0，`skipped` 恒 4（全部来自 live 门禁）。

### 4.1 A1.5 挖出的"错出口地雷"（本轮最有价值的发现）

| 层 | 证据 |
|---|---|
| cookie 按**请求 host** 绑定 | `HCookieJar.kt:35` → `toLoginCookieList(host)`，把 `hanime1_session` / `XSRF-TOKEN` 绑到该 host |
| 持有 `HCookieJar` 的只有 API 出口 | `ServiceCreator.kt:153` 的 `buildHClient()` |
| CDN 出口刻意剥掉站点 cookie | `CdnFetchClient.kt:74-76`："图床是第三方，登录态发过去只有泄漏风险，没有用途" |
| 桌面 Coil 单例原指向 API 出口 | `desktopApp/.../Main.kt`（已修） |

**后果**：任何一次"顺手写个 `AsyncImage(...)` 不带 loader"，用户的站点登录凭据就会发往第三方图床。
修前它是死配置（全仓不带 loader 的取图点为零）所以尚未实际泄漏，但属高危地雷。**已拆。**

### 4.2 未提交状态（重要）

以上改动**全部留在工作树、未提交**，以免与可能的并行会话冲突：
- 修改 7 个文件：`EchGateLiveTest.kt`、`HanimeImageLoader.{jvm,ios}.kt`、`CdnFetchClientTest.kt`、
  `Main.kt`、`SearchScreen.kt`、`SearchViewModel.kt`
- 新增：`PagingGate.kt`、`PagingGateTest.kt`、`HanimeImageLoaderSingletonTest.kt`、
  `DesktopCoilSingletonWiringTest.kt`、`docs/audit/`、`docs/plan/late-stage-hardening.md`
- **产品源码改动仅限 A1 / C / A1.5 三处，均已反向验证并通过 546 用例全绿。**
- 单个文件回滚：`git checkout -- <路径>`；新增文件删除即可。

---

## 5. 待施工队列（已备好规格，按 ROI 排序）

| 序 | 任务 | 规模（已实测） | 施工前提 |
|---|---|---|---|
| 1 | **B 核心模型稳定性**：`var`→`val` + `@Immutable` + typealias 去可变 | **2 文件 3 符号、零调用点**（`isWatchLater`/`isSelected` 全仓零赋值点已核实） | 验收**不绑**编译器报告：CMP 1.12/Kotlin 2.4.10 开 metrics 有 `Internal compiler error` 风险 → 用三重确定性守卫（反射钉 FINAL / getter 返回 `java.util.Map` / 源码扫描钉 `@Immutable`） |
| 2 | **E MyList 分页并发**：收藏 / 稍后再看 | 接口 + 4 实现 + 1 路由文件 | 复用 `PagingGate`；**真源是 `MyListSubViewModel.kt:31` 的共享可变 `isRefreshing`**（跨请求串味导致旧响应被误判为追加），须改为"每请求局部量 + 请求标识" |
| 3 | **D 覆盖缺口**：`Parser` 内联夹具、`video/surface` smoke、`TopLevelBackStack`、seek 边界 | 4 组 | `Parser` 的现有夹具依赖 gitignore 目录，必须换成内联 HTML 字符串 |
| 4 | **A2**：Getchu 那套同构 loader 单例化 | 机械活 | 其 loader 是 **public**，先查调用方；带域名特化拦截器，需独立守卫 |
| 5 | **Artist 弱守卫**：`ArtistViewModel.kt:84-117` | 小 | `load()` 绕过 `if (loading) return`、不 cancel、不校验 userId |
| 6 | 产品差距（首页 Feed / 搜索联想） | 需求级 | 属"内容平台化"，走独立需求流程 |

### 明确不做（防过度重构）
- 不动 `video` 四层分层、不动 `PlaybackUiState` 剔除 `positionMs` 的位置投影（已验证有效的取舍）
- 不引入 Paging3（现有取舍有据，缺的是护栏不是重写）
- 不收敛 Ktor / OkHttp 双引擎
- 不把 `VideoCardItem.kt:344` 那个有单测钉住的空函数体当 bug 修

---

## 6. 验收标准（可判定，缺一不算完成）

1. **有守卫测试**，且该用例能区分正确与错误实现；
2. **做过反向验证**：临时关掉规则 / 改成等价反例，用例**必须转红** —— 不转红即空用例；
   ⚠️ 且要**命中你要证的那条断言**（A1.5 的 RV-B 第一版打红的是"未委派"而非"泄漏"，自我修正后才成立）；
3. **在可信基线上**：`./gradlew :shared:desktopTest --rerun-tasks --offline` 全绿，
   且 XML 的 `testsuite timestamp` 属本轮（Gradle 的 UP-TO-DATE 会重放旧结果）；
4. `SKIP` **单独计数**，不许并进"全绿"。

---

## 7. 未验证 / 存疑（诚实清单）

- CMP 1.12 + Kotlin 2.4.10 开启 Compose 稳定性报告是否真会 `Internal compiler error`：
  **仅来源于一份 AI 生成的取证文档，未复验** —— 故 B 的验收已改为不依赖它。
- 桌面端 `setSingletonImageLoaderFactory` 是否还有其它隐式消费点：本轮枚举结论为"零"，
  但该结论基于静态扫描，未做运行时验证。
- 产品侧"~37 个屏幕"是静态可达性盘点，未做真机逐屏走查。
- 本轮所有改动均通过**桌面套件**验证；Android / iOS 只做了**编译**验证（各目标 0 error），
  未在真机/模拟器上跑过 UI 行为。

---

## 8. 如何接续（下次开工入口）

1. 读 `docs/plan/late-stage-hardening.md`（唯一活计划，含每个任务的逐步规格与反向验证要求）。
2. 先跑一次基线确认环境：`./gradlew :shared:desktopTest --rerun-tasks --offline`
   期望 `546 tests / 0 fail / 4 skipped`。
3. 从 §5 队列第 1 项（任务 B）开始：规模最小、零调用点、守卫可离线独立跑，是"把 `@Immutable`
   纪律铺到自研代码"的第一个样板。
