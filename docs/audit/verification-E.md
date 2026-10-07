# 独立验证报告 · 任务 E（MyList 收藏 / 稍后再看 分页并发改造）

- 验证人：严过关（QA Engineer · software-qa-engineer-2）
- 被验证对象（工程师自报，7 文件 +185/−95，未提交；另新增 2 文件）：
  `MyListRoutes.kt`、`MyListSubViewModel.kt`、`MyListControllers.kt`、`FavSubViewModel.kt`、`WatchLaterSubViewModel.kt`、`LocalFavSubViewModel.kt`、`LocalWatchLaterSubViewModel.kt`
  ＋新增 `commonMain/.../feature/library/MyListPaging.kt`（`nextPageOrNull` / `applyPageResponse` / `MyListPageState`）、`commonTest/.../feature/library/MyListPagingTest.kt`（5 用例）
- 手段：**独立复现 + 对抗性证伪**（临时改产品源码做反向验证，**每次实验后立即还原并用 md5 校验**）
- 约束遵守：只写本报告 + 临时实验；**未永久改动产品源码、未提交、未 `git add`**。所有实验只作用于 commonMain 内的 3 个文件（`PagingGate.kt` / `MyListPaging.kt` / `MyListSubViewModel.kt`），均已还原。
- 时间：2026-10-07（本地 UTC+8，XML timestamp 为 UTC）

> 主理人已先行核实过的项（`feature/library/` 内 grep 零命中、无 `REVERSE-VERIFY-BREAK` 残留、555/0/0/4）**我不重复**，只做本单要求的四件更难的事。

---

## 一句话结论

**通过。** 分页并发语义（判重、陈旧响应淘汰、刷新打断、`loadedPageCount` 只增不减、`finish` 不误放闸门）**均独立复现成立**，抽出的纯函数**确实接在生产路径上**（`MyListSubViewModel` 直接调用，非仅测试调用），**未找到任何功能反例**。
但有两处**测试覆盖缺口**（非行为缺陷，属"无人守护"）：① 守卫套件只测纯函数，**不覆盖 `MyListSubViewModel.launchPage` 的生产接线**（实验 E5 证实：往生产路径注入一条绕过闸门的写入，守卫全绿）；② **本地免登录分支（`LocalFav/LocalWatchLater`）零测试覆盖**。两处均建议后续补测，不阻塞本次验收。

---

## V1. 写入路径清单：列表 / 页数是否**全部**过闸门

对 `feature/library/` 内所有写 `itemsFlow`（列表）与 `mutableLoadedPageCount`（页数）的位置逐一枚举并判定（在线路径 `MyListSubViewModel.kt`，grep 全仓无第六处写点）：

| # | file:line | 写入 | 是否过闸门 | 判定 |
|---|---|---|---|---|
| ① | `MyListSubViewModel.kt:114` | `itemsFlow.value = applied.items` | **是**：`applied = applyPageResponse(...)`（`:100`）在 `!gate.isCurrent(token)` 时返回 `null`，`applied == null` 分支**不写任何状态**（`:111`） | ✅ 合法 |
| ② | `MyListSubViewModel.kt:115` | `mutableLoadedPageCount.value = applied.loadedPageCount` | **是**：同上，`applied` 非空才执行 | ✅ 合法 |
| ③ | `MyListSubViewModel.kt:151` | `deleteItem` 内 `itemsFlow.update { removeAt(position) }` | **否（有意）**：删除**不应**被分页闸门挡——翻页在途时用户删除一条必须立即生效 | ✅ 合法（闸门外） |
| ④ | `MyListSubViewModel.kt:165` | `clearMyListItems` 内 `itemsFlow.value = emptyList()` | **否（有意）**：该函数先 `pagingGate.reset()`（`:161`）作废在途，属"重置路径"本身 | ✅ 合法（闸门外） |
| ⑤ | `MyListSubViewModel.kt:164` | `clearMyListItems` 内 `mutableLoadedPageCount.value = 0` | 同 ④ | ✅ 合法（闸门外） |
| ⑥ | `LocalFav/LocalWatchLater`：`reload()` 写 `itemsFlow.value = list` / `loadedPageCountFlow.value = 1`；`clearMyListItems` 写空 / `0` | 本地路径**无分页语义**（自持字段，非 `MyListSubViewModel`），一次性全量替换 | ✅ 合法 |

**结论**：不存在"绕过闸门的非法写入"。③④⑤ 是**有意**在闸门之外的合法写入（删除 / 重置），且 `deleteItem` / `clearMyListItems` 与旧版**逐字节相同**（未改）——本任务没有新增旁路。

**真实引用确认**：`nextPageOrNull` 生产引用 `MyListSubViewModel.kt:64`；`applyPageResponse` 生产引用 `MyListSubViewModel.kt:100`。二者**不是只在测试里出现**。✅

文档残留（`MyListPaging.kt:14-21`、`MyListControllers.kt:14`、`MyListSubViewModel.kt:24`）中对 `favVideoPage` / `getMyFavVideoItems` / `isRefreshing` 的提及**仅存在于 KDoc 注释**，非代码引用。

**⚠️ V1 的诚实边界（实验 E5 揭示，见 §V2）**：以上判定**完全来自源码静态审阅**——因为 `MyListSubViewModel` 依赖 `NetworkRepo` 单例，headless 起不来，**没有任何测试真的驱动 `launchPage` 的闸门接线**。我实测：往生产路径注入一条绕过闸门的写入，13 条守卫**全绿**。因此"生产路径过闸门"目前**只由代码审阅保证，不被任何执行中的测试守护**——这是覆盖缺口，不是行为错误。

**⚠️ 既有风险（pre-existing，非本任务引入）**：`deleteItem`（`:151`）用**位置下标** `removeAt(position)`。若删除成功回调落在一次 `clearMyListItems`（清空）之后，`removeAt(position)` 会 `IndexOutOfBoundsException`；若期间列表被刷新替换，还会**错删**。此代码与 UI 侧 `indexOfFirst` 位置计算**均与旧版一致、未被本任务改动**，故记为既有风险，不计入本次验收。

---

## V2. 守卫是否有区分力、是否真空（反向验证复现表）

跑法：`./gradlew :shared:desktopTest --tests "<类>" --rerun-tasks --offline`，计数取自 `shared/build/test-results/desktopTest/*.xml` 的 `testsuite` 属性。每次破坏后**立即还原**（md5 校验，见 §还原证据）。

### 反向验证复现表

| 实验 | 破坏项 | 期望红用例 | 实际结果 | 转红断言原文 | 精确命中 |
|---|---|---|---|---|---|
| **E1** | `PagingGate.tryBegin()` 删掉 `if (inFlight) return null`（**恒返还凭证**） | 判重用例 | MyListPagingTest `tests=5 failures=1`；红=`在途时再次触底被拒` @ `MyListPagingTest.kt:61` | `在途时 tryBegin 仍放行：判重失效，会同时存在两个在途请求 expected null, but was:<...PagingGate$Token@...>` | **是**——红的就是判重那条 `assertNull(gate.tryBegin())`（同轮 `PagingGateTest` 亦红 2 条，含它自己的判重用例） |
| **E2** | `PagingGate.isCurrent()` 改为恒 `true` | 陈旧响应用例 | MyListPagingTest `tests=5 failures=3`；红=`过期凭证的响应既不并入列表也不回退 loadedPageCount` @ `MyListPagingTest.kt:95` | `过期凭证的响应通过了闸门：会并入旧页 / 回退 loadedPageCount expected null, but was:<MyListPageState(items=[HanimeInfo(...p1-0...)...` | **是**——红的就是那条 `assertNull(stale, ...)`；同轮 `refresh 能打断在途…`(151) 与 `finish 只释放仍有效凭证…`(172) 亦红（均由 `isCurrent` 支撑） |
| **E3** | `applyPageResponse` 内 `maxOf(loadedPageCount, page)` → **直接赋值 `page`** | 页数只增不减用例 | MyListPagingTest `tests=5 failures=1`；红=`有效响应的页数只增不减_且刷新为替换而非追加` @ `MyListPagingTest.kt:134` | `陈旧页把 loadedPageCount 往回拨了 expected:<2> but was:<1>` | **是**——红的就是"不回退页数"那条 `assertEquals(2, late.loadedPageCount, ...)` |
| **E4**（追加） | `applyPageResponse` 内 `(base + incoming)` → **`(incoming + base)`**（追加顺序反转） | "第 2 页按序落尾"用例 | MyListPagingTest `tests=5 failures=1`；红=同上用例 @ `MyListPagingTest.kt:124` | `第 2 页应按序追加在尾部 expected:<[p2-0, p2-1, p2-2]> but was:<[p1-0, p1-1, p1-2]>` | **是**——证明"按序落尾"断言**有区分力**，不是摆设 |

→ **① 判重**（E1）、**② 陈旧响应**（E2）、**③ 只增不减**（E3）**各自恰好打红对应断言**，且 E4 证明顺序断言同样可被证伪。工程师这几条自述**成立**。

### 5 条用例是否为空用例？——**不是**

逐条核对断言对象（`MyListPagingTest.kt`），无一条是"列表非空"之类的松断言：

1. `在途时再次触底被拒`：钉 `nextPageOrNull(inFlight=true) == null`（**判重**）+ `tryBegin() == null`（**闸门判重**）。非空。
2. `过期凭证的响应既不并入列表也不回退 loadedPageCount`：钉 `stale == null`（陈旧响应不放行）。非空（E2 已证）。
3. `有效响应的页数只增不减_且刷新为替换而非追加`：钉**刷新替换**（`p1.items == [p1-0,p1-1,p1-2]`、无 `stale-x`）、**第 2 页按序落尾**（`takeLast(3)==[p2-0,p2-1,p2-2]`）、**旧页不回退**（E3/E4 已证三条各自可被证伪）。非空，且正是"不靠 `distinctBy` 掩盖"的那几条。
4. `refresh 能打断在途请求且新请求可正常开始`：钉 `isCurrent(旧)==false`、`isCurrent(新)==true`、`inFlight` 状态流转。非空（E2 同轮转红）。
5. `finish 只释放仍有效的凭证_旧请求晚到不误放闸门`：钉旧 `finish` 不误放新轮闸门。非空（E2 同轮转红）。

**两点诚实的弱化说明（不改判定）**：
- 用例 2 的"调用方在 `stale==null` 时不改任何状态"是**在测试内复刻** VM 的收集分支（断测试自己的局部变量），**不是**直接驱动 VM；真正的"页数不回退"由 `maxOf` 实现、由**用例 3**钉住（E3 已证）。用例 2 的实质断言是 `assertNull(stale)`。**建议**该说明写进用例注释以免误读。
- 用例 1 末句 `assertEquals(2, first, "判重失败不该推进页号")` 的注释表意偏弱（`first` 是本地不可变值，此断言实际钉的是"`nextPageOrNull` 返回 `count+1` 而非 `count`"）。非缺陷，仅提示。

---

## V3. 本地免登录分支（`LocalFav` / `LocalWatchLater`）不能只"看着对"

**结论：未发现行为回归；但该分支零测试覆盖（覆盖缺口）。**

1. **`refresh()` 重新订阅后列表不会被清空且能刷新 —— 成立，语义未丢。**
   新 `refresh()`（`LocalFavSubViewModel.kt:47-50`）= `clearMyListItems()` + `reload()`；`reload()`（`:53-63`）`loadJob?.cancel()` 后重新 `collect(LocalListRepository.observeFavorites())`，**一次性灌入全部条目**并置 `loadedPageCountFlow=1`、`isLoadingMore=false`、`state=NoMoreData`。
   与**旧版对照**：旧 `getMyFavVideoItems(userId,page)`（HEAD 版）代码与旧 `refresh()` 路由 `clearMyListItems()+getMyFavVideoItems()` **逐句等价**——`clearMyListItems` 语义未被弄丢，`reload()` 就是旧订阅体的原样提取。✅

2. **`loadNextPage()` 恒 `false` 时 UI 不会卡"加载中"、不会反复触发 —— 成立。**
   - 本地 `reload()` 后 `state = NoMoreData`；`LazyGridState.canLoadMore(...)`（`VideoGridUtils.kt:19`）在 `NoMoreData` 时**恒 `false`** → `onLoadMore()` 永不触发，无循环。
   - `LoadMoreFooter`（`LoadMoreFooter.kt:65-74`）对 `NoMoreData` 渲染"**加载完成**"，**不是** loading 转圈；`isLoadingMore=false`。不会卡在"加载中"。
   - 澄清：`loadNextPage()` 返回 `false` 只表示"没有下一页"，UI 未依据返回值做重试，故返回 `false` 不会引发反复触发。

3. **"`isRefreshing=true` 时替换而非追加"的旧语义 —— 在本地路径上天然成立，无回归。**
   本地实现**从不继承 `MyListSubViewModel`**，旧版也**从未**有 `isRefreshing` 字段——它始终 `itemsFlow.value = list`（**整体替换**）。故"替换语义"在本地路径上依旧（且只能）成立，不存在被弄丢的问题。

4. **覆盖缺口（明确）**：`LocalFavSubViewModel` / `LocalWatchLaterSubViewModel` **没有任何自动化测试**（grep 全仓测试目录零命中）。上述 1–3 的结论**只能靠源码对照（与 HEAD 版逐句比对）得出**，不被任何用例守护。→ 记为覆盖缺口（与本任务"抽纯函数"的范围一致——本地分支本就不含分页逻辑，但 `refresh()` 的重订阅语义值得补一条测试）。

5. **既有观察（pre-existing，非本任务引入）**：`MyListVideoGridScreen.kt:108` 的 `shouldBootstrap`（`items.isEmpty() && state is Loading && loadedPageCount==0`）在该文件**未被本任务改动**；`refresh()` 先 `clearMyListItems()` 造成的"瞬时空列表 + Loading"窗口会让 `shouldBootstrap` 短暂转真，存在一次**冗余的 bootstrap 刷新**。此行为在旧路由（同样 `clear + get…`）下**已存在**，非本任务引入，仅记录。

---

## V4. 全量 + 三端 + 残留

### V4-1 全量回归（我亲手跑）

`./gradlew :shared:desktopTest --rerun-tasks --offline` → `BUILD SUCCESSFUL in 1m 59s`。

| 指标（逐 XML 汇总） | 数值 |
|---|---|
| XML 文件数 | **103** |
| tests | **555** |
| failures | **0** |
| errors | **0** |
| skipped | **4** |

- **timestamp 窗口（UTC）**：`2026-10-07T07:06:38.546Z → 2026-10-07T07:07:05.007Z`（本地 15:06:38 → 15:07:05），落在本轮 `--rerun-tasks` 运行窗内 → 本轮真跑。
- 守卫类单独确认：`MyListPagingTest[desktop]` **tests=5 / fail=0 / err=0 / skip=0**（ts `07:06:52.178Z`）；`PagingGateTest[desktop]` **tests=8 / fail=0**（ts `07:06:39.845Z`）。

### V4-2 skipped 归属（4 条，无新增跳过）

4 条 skipped **全部**来自 `lovehan1me.data.network.EchGateLiveTest`，用例名：
`不经网关直连站点必然失败`、`经ECH网关直连站点拿到200`、`视频CDN经网关可建立连接`、`经ECH网关javchu拿到200`。
→ **无任何新增跳过**，没有被悄悄关掉的用例。✅

### V4-3 三端编译（删了接口成员，必须确认无其它模块引用）

`./gradlew :shared:compileKotlinDesktop :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64 --rerun-tasks --offline`
→ `BUILD SUCCESSFUL in 5m 38s`；三个任务**均强制重编译执行**（非 UP-TO-DATE）：
`> Task :shared:compileKotlinDesktop` ✅ / `> Task :shared:compileAndroidMain` ✅ / `> Task :shared:compileKotlinIosSimulatorArm64` ✅

- 已删成员 `favVideoPage` / `watchLaterPage` / `getMyFavVideoItems` / `getMyWatchLaterItems`：全仓（排除 `reference/` 只读副本）grep = **零命中**（仅 3 处 KDoc 注释提及）。三端零编译错误 ⇒ **无其它模块仍引用被删成员**。✅

---

## 还原证据（证明无实验残留）

- 实验前先把 3 个待变异文件复制到 `/tmp/qaE_backup/`；每次破坏后 `cp` 还原并 `diff -q`，**三次全部 `RESTORED-OK`**。
- 末次 `md5` 与备份**逐一 IDENTICAL**：
  - `core/domain/state/PagingGate.kt` → `74d558656facec3753a2751eac62ef98`
  - `feature/library/MyListPaging.kt` → `15c794f3e6449ac0e8190b9dc2059d04`
  - `feature/library/MyListSubViewModel.kt` → `0f33aac4ae2bf3508d6a6a8bcf62cd50`
- 全仓 `grep -rn "REVERSE-VERIFY-BREAK" shared/src` → **无残留**。
- `git status --porcelain` 与实验前**逐行一致**（16 modified + 8 untracked 条目，无新增/删除）；本报告写入 `docs/audit/`（已是 untracked 目录，不引入新状态项）。**未执行任何 `git add` / `git commit`。**

> 说明：工作树实际改动**多于**任务 E 的 7 文件（另有 `Main.kt`、`SearchScreen/ViewModel.kt`、`HanimeVideo.kt`、`HanimeResolution.kt`、`HanimeImageLoader.jvm/ios.kt`、`EchGateLiveTest/CdnFetchClientTest.kt` 等及 `PagingGate.kt`）——这些属**其它任务（A/B/C）**，非本任务 E 范围；任务 E 的切片经 `git diff --stat` 精确核对确为 **7 文件 +185/−95**，与自报一致。

---

## 与工程师自述不一致之处

**硬性不符：无。** 工程师五条核心自述我**全部独立复现成立**：
① 删除共享 `isRefreshing`，刷新语义由 `PagingGate.restart()` 凭证表达 —— 成立（E2 证 `isCurrent` 是承重墙）；
② 陈旧响应写状态前过 `applyPageResponse`→`gate.isCurrent` —— 成立（E2）；
③ `loadedPageCount` 用 `maxOf` 只增不减 —— 成立（E3）；
④ `gate.finish(token)` 在 `finally` 且只释放仍有效凭证 —— 成立（`MyListSubViewModel.kt:135-137` + `PagingGate.finish` 判 `isCurrent`；E2 同轮用例 5 转红佐证）；
⑤ UI 不再读写页序号 —— 成立（`MyListRoutes.kt` 仅 `fav.refresh()` / `fav.loadNextPage()`，无页号读写）。

**需补充的局限（自述未覆盖，非"不符"）：**

1. **守卫只覆盖抽出的纯函数，不覆盖 `MyListSubViewModel` 的生产接线**——实验 E5：往 `launchPage` 注入 `itemsFlow.value = state.info.hanimeInfo`（一条绕过闸门、无视 `applied==null` 的写入），`MyListPagingTest`+`PagingGateTest` **13 条全绿（fail=0，ts 07:17:24Z）**。即：**将来若有人在生产路径上加一条旁路写入，现有守卫一条都抓不住**。V1 的"生产路径过闸门"结论**仅由源码审阅保证**。建议：若可行，为 `launchPage` 的闸门接线补一条可 headless 运行的测试（或至少把该接线的判定函数再收窄）。
2. **本地免登录分支零测试覆盖**（见 V3-4）。
3. 次要：用例 2 的"调用方不改状态"是测试内复刻；用例 1 末句断言注释表意偏弱（均见 V2 末）。

以上 1、2 为**覆盖缺口**（建议后续补测），**均不构成行为缺陷或回归**；3 为低风险提示。**本次验收判定：通过。**
</content>
</invoke>
