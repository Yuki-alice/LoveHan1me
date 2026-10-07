# 独立验证报告 · 任务 B（核心模型稳定性改造）

- 验证人：严过关（QA）
- 被验证对象：`HanimeVideo.kt`（`@Immutable` + 2 字段 `var`→`val`）、`HanimeResolution.kt`（typealias 收窄为 `Map`）、新增守卫测试 `ModelStabilityGuardTest.kt`
- 手段：独立复现 + 对抗性证伪（改产品源码做临时实验，**每次实验后立即还原**）
- 约束遵守：只写本报告文件 + 临时实验；**未永久改动产品源码、未提交**

---

## 一句话结论

**有条件通过。** 三处改动真实存在且语义正确，`@Immutable` 契约**诚实**（未找到可复现的反例），三条守卫**各有真实区分力**；但守卫覆盖不完整——`HanimeVideo` **自身字段的 `val` 性没有任何守卫**（可被改成 `var` 而三条守卫全绿）。建议补 1 条用例，无需回退当前改动。

---

## A. 独立复现三条反向验证

跑法：`./gradlew :shared:desktopTest --tests "lovehan1me.core.domain.model.ModelStabilityGuardTest" --offline --rerun-tasks`（单类）。计数取自 `shared/build/test-results/desktopTest/TEST-...ModelStabilityGuardTest.xml` 的 `testsuite` 属性。

### A1 — 两个字段改回 `var`

| 项 | 内容 |
|---|---|
| 破坏项 | `HanimeVideo.MyList.isWatchLater`、`MyListInfo.isSelected`：`val` → `var` |
| 期望红用例 | ① `MyList_state fields are val (final backing field)` |
| 实际结果 | `tests=3 failures=1`；红 = 用例①，`ModelStabilityGuardTest.kt:41` |
| 断言消息原文 | `java.lang.AssertionError: 契约被破坏：HanimeVideo.MyList.isWatchLater 必须是 val（其 backing field 应为 final）。实测修饰符=private。改回 var 会让 HanimeVideo 的 @Immutable 承诺变成谎言，Compose 会错误跳过重组。` |
| 精确命中 | **是**（红的就是本规则断言；仅先触发 isWatchLater 一条，isSelected 因短路未执行——属预期） |
| 运行窗口 / XML ts | 14:11:47–14:12:37 ／ `2026-10-07T06:12:36.959Z` |

### A2 — typealias 改回 `LinkedHashMap`

| 项 | 内容 |
|---|---|
| 破坏项 | `typealias ResolutionLinkMap = Map<String, HanimeLink>` → `LinkedHashMap<String, HanimeLink>` |
| 期望红用例 | ② `videoUrls getter declares read-only java_util_Map` |
| 实际结果 | `tests=3 failures=1`；红 = 用例②，`ModelStabilityGuardTest.kt:67` |
| 断言消息原文 | `java.lang.AssertionError: 契约被破坏：HanimeVideo.videoUrls 的声明返回类型必须是只读的 java.util.Map，实测为 java.util.LinkedHashMap。类型一旦回退到具体可变实现类，消费它的模型会被 Compose 判定为 unstable，详情页的跳过优化随之丢失。` |
| 精确命中 | **是**（`实测为 java.util.LinkedHashMap` 即本规则要抓的返回类型回退） |
| 运行窗口 / XML ts | 14:13:03–14:13:47 ／ `2026-10-07T06:13:46.917Z` |

### A3 — 删除 `@Immutable`

| 项 | 内容 |
|---|---|
| 破坏项 | 删除 `HanimeVideo` 上方的 `@Immutable` |
| 期望红用例 | ③ `HanimeVideo is annotated with adjacent @Immutable` |
| 实际结果 | `tests=3 failures=1`；红 = 用例③，`ModelStabilityGuardTest.kt:106` |
| 断言消息原文 | `java.lang.AssertionError: 契约被破坏：HanimeVideo 的声明上方必须紧邻 @Immutable（androidx.compose.runtime.Immutable），实测其上的注解块为 [@Serializable]。缺了它，Compose 不会再信任本类的不可变性，详情页重建时整棵子树都将被迫重组。` |
| 精确命中 | **是**（`实测其上的注解块为 [@Serializable]` 直指缺注解） |
| 运行窗口 / XML ts | 14:14:11–14:14:58 ／ `2026-10-07T06:14:57.979Z` |

**A 小结**：三条反向验证各自**恰好打红 1 条**，且红的就是对应规则的断言——工程师该自述**成立**，我独立复现无误。

---

## B. 守卫 ③ 是不是"松断言"？——**不是**

**实验**：把 `@Immutable` 从 `HanimeVideo` **挪到同一文件里的另一个声明**（`data class MyList` 上方），使 `HanimeVideo` 上方的注解块只剩 `@Serializable`。此时文件里**仍然出现** `@Immutable` 字符串。

**结果**：`tests=3 failures=1`，红的仍是 **用例③**，消息：

> `契约被破坏：HanimeVideo 的声明上方必须紧邻 @Immutable …… 实测其上的注解块为 [@Serializable]。`

**结论**：守卫③ **具备真实区分力**，不是"文件里出现该字符串"级别的松断言。其实现只收集声明上方**连续**的 `@` 行（`while (lines[cursor].trim().startsWith("@"))`），注解一旦被挪走就失去紧邻关系 → 立即打红。team-lead 对 B 的怀疑**不成立**。

> 次要残留（低风险）：守卫③只做**注解名**匹配，不校验 `@Immutable` 是否解析到 `androidx.compose.runtime.Immutable`（不检查 import）。理论上"同名自定义注解"能冒充。现实风险极低，仅作提示。

---

## C. `@Immutable` 契约本身是否诚实？

**结论：诚实——未找到"构造后被原地改写"的可复现路径。** 存在若干**低频 latent 风险点**（如下），全部是"运行时持有可变实例、声明为只读类型"，当前**不可达**。

证伪尝试与证据：

1. **`videoUrls` 是否被可变转换/写操作**：全仓搜 `as LinkedHashMap` / `as MutableMap` / `videoUrls.put|remove|clear` / `videoUrls[...] =` → **零命中**。全部 `videoUrls` 用法皆为读：`.map{}`、`.keys`、`[q]?.link`、`[q]?.suffix`、`.isEmpty()`、`.size`、`.first()`、`.drop(1)`、`.containsKey()`。
2. **`toResolutionLinkMap()` 构造链是否被上游保留再改**：`HanimeResolution.kt:70-72` 每次调用都 `resArray.filterNotNull().toMap(linkedMapOf())` **新建**实例。调用点 `Parser.kt:659`、`VideoViewModel.kt:260` / `:299`、`VideoCacheStore.kt:25`、`HanimeCacheManager.kt:150/155` 均为"构造即传入构造函数"，**无上游保留同一实例后继续改**（`Parser` 的 `hanimeResolution` 是 `:592` 的局部 val）。
3. **`myListInfo` 是否被构造后改写**：`Parser.kt:428` 的 `mutableListOf` 在循环内 `+=` 后于 `:444` 作为构造实参传入，其后不再触碰 → **构造时写**（无害）；`VideoViewModel.kt:403` 的 `.toMutableList()` 局部改写后于 `:405` 立即 `.copy(myListInfo = …)` → **构造时写**（无害）。**未发现"构造后持有再写"**。
4. **字段声明**：`HanimeVideo` 全部字段为 `val`，字段声明类型均为 `String/Int/Boolean/LocalDate?/List/Map/<不可变 data class>`——**无 `MutableXxx` 或函数类型字段**。

**风险点（latent，当前不可达）**：

| # | file:line | 现象 | 触发条件 |
|---|---|---|---|
| 1 | `HanimeResolution.kt:70-72` | 声明返回 `Map`，运行时实例是 `LinkedHashMap`（可变） | 未来有人拿到 `video.videoUrls` 后强转/写回，并让同一实例在别处复用 |
| 2 | `Parser.kt:428 / :444` | 把可变 `ArrayList` 塞进 `List` 声明字段 | 未来有人在构造后继续持有该 list 并增删 |
| 3 | `VideoViewModel.kt:403-405` | 同上（`toMutableList()` 结果进入 `copy()`） | 同上 |

→ 结论：**契约当前成立，但保证来自"纪律"而非类型系统**（与 `HanimeVideo.kt` KDoc 的告警一致）。本次改动**不构成谎报**。

---

## D. 全量回归 + 三端编译

### D1. `:shared:desktopTest --rerun-tasks --offline`（全量）

- **逐文件汇总**（`shared/build/test-results/desktopTest/*.xml`，共 **102** 个 XML）：

  | 指标 | 数值 |
  |---|---|
  | tests | **549** |
  | failures | **0** |
  | errors | **0** |
  | skipped | **4** |

- **timestamp 窗口（UTC）**：`2026-10-07T06:18:42.266Z` → `2026-10-07T06:19:31.391Z`（本地 14:18:42 → 14:19:31），落在 `--rerun-tasks` 全量运行窗口 **14:17:50–14:19:32** 之内 → 证明是**本轮真跑**（非重放旧 XML）。
- 守卫类单独确认：`ModelStabilityGuardTest[desktop]` `tests=3 skipped=0 failures=0 errors=0`。

### D2. skipped 归属核对（4 条，无新增跳过）

4 条 skipped **全部**来自 `lovehan1me.data.network.EchGateLiveTest`，均为 `org.junit.AssumptionViolatedException: … 需要 HAN1ME_ECHGATE_LIVE：本用例会拉起真实网关进程并访问真实公网`：

1. `不经网关直连站点必然失败`
2. `经ECH网关直连站点拿到200`
3. `视频CDN经网关可建立连接`
4. `经ECH网关javchu拿到200`

→ **没有新增跳过**，没有用例被悄悄关掉。

### D3. 三端编译（`--rerun-tasks --offline`，强制重跑）

| 任务 | 结果 |
|---|---|
| `:shared:compileKotlinDesktop` | 执行，成功 |
| `:shared:compileAndroidMain` | 执行，成功 |
| `:shared:compileKotlinIosSimulatorArm64` | 执行，成功 |

`BUILD SUCCESSFUL in 2m 3s`，窗口 14:21:30–14:23:35。三端均**强制重编译**（非 UP-TO-DATE），确认未碰坏其他平台。

---

## E.（追加）对抗性发现：守卫覆盖不完整

工程师自述的是"3 条用例"，本身没错；但从"保护 `@Immutable` 契约"的**目的**看，守卫有缺口。我做了一个更难的反例：

**实验**：把 `HanimeVideo.videoUrls` 由 `val` 改成 **`var`**（直接违反 `@Immutable` 前提"全类字段皆 `val`"）。

**结果**：`tests=3 failures=0` —— **三条守卫全绿**（运行窗口 14:16:34–14:17:24，XML ts `2026-10-07T06:17:23.836Z`）。

原因：守卫①只钉 `MyList` 的**两个**字段；守卫②只看 getter 的**返回类型**（`var videoUrls` 的 getter 仍是 `java.util.Map`）；守卫③只看注解紧邻。**三者都不涉及 `HanimeVideo` 自身字段的 `val` 性**。

**影响**：将来往 `HanimeVideo` 加一个 `var` 字段、或把任一现存字段改成 `var`，**都不会被现有守卫打红**——而这正是 KDoc 明令禁止、会把契约变成谎言的改动。**建议补第 4 条用例**：反射遍历 `HanimeVideo` 的声明字段，断言全部 `Modifier.isFinal`（并可顺带断言不存在 `MutableMap/MutableList` 声明类型）。

> 次要观察：守卫②的第二条断言 `returnType != linkedHashMapClass`，在第一条 `returnType == mapClass` 通过后**恒真**（`Map ≠ LinkedHashMap`），属冗余断言。不构成缺陷，仅提示。

---

## F. 还原证据（证明无实验残留）

- `git status --porcelain` 仍为原来的 **9 个 modified + 7 个 untracked**，**无任何新增/删除**（实验未落盘任何文件）。
- 两个产品文件的 `git diff` 与工程师改动**逐字节一致**：
  - `HanimeVideo.kt`：`+import androidx.compose.runtime.Immutable`、KDoc 增补、`@Immutable`、`-var/isSelected`→`+val/val`（22 insertions / 2 deletions）
  - `HanimeResolution.kt`：KDoc 增补、`-typealias … LinkedHashMap`→`+typealias … Map`（8 行）
- 对照实验期间生成的临时差异（`var videoUrls` / 挪动 `@Immutable` / 改回 `LinkedHashMap`）**均已复制还原**，且 `diff -q` 与备份**完全一致**（输出 `IDENTICAL to backup`）。
- 未执行任何 `git add` / `git commit`。

---

## 与工程师自述不符之处

**硬性不符：无。** 工程师的五条自述（①加 `@Immutable`；②两字段 `var`→`val`；③typealias→`Map`；④新增 3 条守卫用例；⑤"三条反向验证各自恰好打红 1 条且命中本规则"）我**全部独立复现成立**。其行号标注（`:103/:109`、`:13`）是**改动前**的行号，落在改动前位置上，属内部一致的近似表述，非错误。

**需补充的局限（自述未覆盖）**：

1. **守卫只覆盖 `MyList` 两字段与注解紧邻关系，不覆盖 `HanimeVideo` 自身字段的 `val` 性**（见 §E 实验：`var videoUrls` 下三条守卫全绿）。这是"守卫套件能否真正守住 `@Immutable`"的实质性缺口。
2. **守卫③不校验 `@Immutable` 的 import 来源**（只做注解名匹配），理论可被同名注解冒充。
3. **守卫②第二条断言恒真**（冗余）。

以上 2、3 为低风险提示；第 1 条建议直接补用例。

---

# 再验证：守卫 ④（`all instance fields across the immutable tree are val`）

- 时间：2026-10-07（本地 14:30–14:39）
- 被验对象：`ModelStabilityGuardTest.kt` 新增第 4 条用例（反射遍历 `HanimeVideo` / `MyList` / `MyListInfo` 的 `declaredFields`，过滤 static/synthetic，断言无非 final 字段）
- 结论预告：**④ 通过；非空跑；与工程师自述一致**（唯一需补充的说明见末节）。

## R1. 独立复现关键反向验证（`videoUrls` `val`→`var`）

跑法：`./gradlew :shared:desktopTest --tests "lovehan1me.core.domain.model.ModelStabilityGuardTest" --offline --rerun-tasks`

| 项 | 内容 |
|---|---|
| 破坏项 | `HanimeVideo.videoUrls`：`val` → `var` |
| 期望 | ④ 转红且消息指名 `videoUrls`；**①②③ 同轮保持绿** |
| 实际结果 | `tests=4 failures=1`；红 = **④**，`①②③` 全 PASS（同轮） |
| 断言消息原文 | `java.lang.AssertionError: 契约被破坏：HanimeVideo 及其可达嵌套类（MyList / MyListInfo）的**所有实例字段**都必须是 val（backing field 为 final），才能支撑 @Immutable 的承诺。违规字段：` ⏎ `HanimeVideo.videoUrls 必须是 val（实测修饰符=private）` ⏎ `把任一字段改成 var 都会让 @Immutable 从契约退化为谎言……` |
| 是否精确命中 | **是**（唯一红的就是 ④，且消息点名 `videoUrls`；`①②③` 同轮绿——证明 ④ 补上的正是原先无人守的空档） |
| 运行窗口 / XML ts | 14:32:09–14:33:16 ／ `2026-10-07T06:33:15.550Z` |

- 基线先验：改前四条约全绿（`tests=4 failures=0`，ts `06:31:46.876Z`）。
- R1 后**立即还原**，`HanimeVideo.kt` 与备份 `diff -q` 完全一致；`val videoUrls` 复现于第 44 行。

## R2. 证明守卫 ④ 不是空跑

### R2a — `MyList.isWatchLater` `val`→`var`

| 项 | 内容 |
|---|---|
| 实际结果 | `tests=4 failures=2`；红 = **①** 与 **④**；②③绿 |
| ④ 的消息 | `……违规字段： MyList.isWatchLater 必须是 val（实测修饰符=private）……` |
| 是否精确命中 | **是**（④ 转红并指名 `isWatchLater`） |
| 附带 | ① 同轮也红（① 本就显式钉这两个字段），属**设计内重叠**，非异常 |
| 窗口 / ts | 14:33:46–14:34:35 ／ `06:34:34.659Z` |

### R2b — `MyListInfo.isSelected` `val`→`var`

| 项 | 内容 |
|---|---|
| 实际结果 | `tests=4 failures=2`；红 = **①** 与 **④**；②③绿 |
| ④ 的消息 | `……违规字段： MyListInfo.isSelected 必须是 val（实测修饰符=private）……` |
| 是否精确命中 | **是**（④ 转红并指名 `isSelected`） |
| 窗口 / ts | 14:34:58–14:35:45 ／ `06:35:44.510Z` |

→ 两次实验中 **④ 每次都转红且点名对应字段**，从未出现"只有①②红、④仍绿"的情形。**④ 的覆盖与其自称一致。**（①② 与 ④ 在这两个字段上重叠，是刻意设计：① 是窄化钉子，④ 是全集钉子。）

### R2c — 直接证明"遍历到的字段非空且含关键字段"（临时打印，已还原）

临时在 ④ 内插入探针，打印每个类的 `declaredFields` 及过滤后的**被检查集合**，实跑输出（原样摘录）：

```
[guard4-probe] HanimeVideo declaredFields -> Companion{Companion:static,final} | title{String:final} | coverUrl{String:final} | chineseTitle{String:final} | introduction{String:final} | uploadTime{LocalDate:final} | views{String:final} | videoUrls{Map:final} | tags{List:final} | myList{MyList:final} | playlist{Playlist:final} | relatedHanimes{List:final} | artist{Artist:final} | favTimes{Integer:final} | isFav{boolean:final} | unlikesCount{Integer:final} | isUnlike{boolean:final} | csrfToken{String:final} | currentUserId{String:final} | originalComic{String:final} | $stable{int:static,final} | $childSerializers{Lazy[]:static,final}
[guard4-probe] MyList declaredFields -> isWatchLater{boolean:final} | myListInfo{List:final} | $stable{int:static,final}
[guard4-probe] MyListInfo declaredFields -> code{String:final} | title{String:final} | isSelected{boolean:final} | $stable{int:static,final}
[guard4-probe] CHECKED(24) = [HanimeVideo.title, …, HanimeVideo.videoUrls, …, MyList.isWatchLater, MyList.myListInfo, MyListInfo.code, MyListInfo.title, MyListInfo.isSelected]
```

- **CHECKED(24)**：④ 实际逐一校验 **24 个实例字段**（非零），且**包含** `HanimeVideo.videoUrls`、`MyList.isWatchLater`、`MyListInfo.isSelected` 三个关键字段。**非真空遍历。** 若把过滤条件反过来（`filter { isFinal }`），违规集变成这 24 条而非空，断言会立刻失败——同样佐证集合非空。
- **被排除的字段是什么、为什么不算"构造后可达状态"**：仅 3 类，全部为 **static**：
  - `Companion`（`@Serializable` 生成的伴生对象引用，类级共享）；
  - `$stable`（Compose 稳定性标记，`static int`，类级编译期常量）；
  - `$childSerializers`（`@Serializable` 生成的子序列化器缓存，`static`）。
  三者都是**类级**、不随实例构造而进入可达状态，排除**正确且必要**（不排除只会把非实例状态误当契约对象）。
- **"synthetic 过滤"当前是 no-op**：三个类**都不存在** synthetic 字段（见上，无任何 `synthetic` 标记）——该过滤是**防御性**写法，当前**不掩盖任何东西**。
- **过滤不构成"掩盖"的证明**：被排除的每个字段在探针里都带 `final` 标记。即便把它们纳入校验也**不会**产生违规，故排除**不可能**是把一个真实的非 final 字段"藏"起来。

**空跑判定：否。** ④ 的遍历既非空、又含关键字段，过滤条件不掩盖违规。

> 注：探针为临时实验，跑完**已还原**（`diff -q` 与备份一致，`grep -c guard4-probe` = 0）。

## R3. 全量回归

`:shared:desktopTest --rerun-tasks --offline`，逐文件汇总 `shared/build/test-results/desktopTest/*.xml`（共 **102** 个）：

| 指标 | 数值 |
|---|---|
| tests | **550** |
| failures | **0** |
| errors | **0** |
| skipped | **4** |

- timestamp 窗口（UTC）：`2026-10-07T06:38:18.018Z` → `2026-10-07T06:38:39.970Z`（本地 14:38:18→14:38:39），落在运行窗口 **14:37:17–14:38:42** 内 → 本轮真跑。
- 守卫类：`ModelStabilityGuardTest[desktop]` `tests=4 skipped=0 failures=0 errors=0`（较上轮的 549 +1，即新增的 ④）。
- skipped 4 条**全部**属 `lovehan1me.data.network.EchGateLiveTest`（`不经网关直连站点必然失败` / `经ECH网关直连站点拿到200` / `视频CDN经网关可建立连接` / `经ECH网关javchu拿到200`），**无新增跳过**。

## R4. 残留与守约

- `git diff --stat`：仍为**同样 9 个文件**、`378 insertions(+), 108 deletions(-)`（与再验证开始时逐字节相同），**无新增改动**。工程师本次只改了**未跟踪**的测试文件 `ModelStabilityGuardTest.kt`（该文件不在 `git diff` 覆盖范围内，属 `?? shared/src/desktopTest/kotlin/lovehan1me/core/domain/`），**产品源码未动**。
- `git diff` 中**唯一**提及 `videoUrls` 的是一行 KDoc 注释（`+ *  - [videoUrls] 的类型由……`）；**字段声明行 `val videoUrls: ResolutionLinkMap` 相对 HEAD 无任何变化** → `videoUrls` 仍是 `val`。
- `git status --porcelain` 仍为 **16** 项（9 M + 7 ??），与再验证开始时一致。
- 我的两次临时实验文件修改（`HanimeVideo.kt` 三次、`ModelStabilityGuardTest.kt` 一次）**全部还原**，两文件与我自己建的备份 `diff -q` **完全一致**。未 `git add`、未 `commit`。

## 是否与工程师自述一致

**一致。** 工程师自述的三点我**逐条独立复现成立**：

1. 「把 `videoUrls` 改回 `var` 时 ④ 唯一转红、①②③ 同轮保持绿」——**R1 完全一致**（`failures=1`，红=④且点名 `videoUrls`）。
2. 「全量 550/0/0/4」——**R3 完全一致**（逐 102 个 XML 汇总，4 skipped 全属 `EchGateLiveTest`，无新增）。
3. 「产品源码未动」——**R4 一致**（`videoUrls` 字段行无 diff，仅测试文件被改，且该文件本是未跟踪）。

**需补充说明（非矛盾，自述未提及）**：把 `MyList.isWatchLater` 或 `MyListInfo.isSelected` 改回 `var` 时，**① 会与 ④ 同轮一起转红**（① 本就显式钉这两个字段）。这是 ①（窄钉子）与 ④（全集钉子）在公共字段上的**设计内重叠**，不是"④ 覆盖与自称不符"——④ 每次都独立转红并点名对应字段。

