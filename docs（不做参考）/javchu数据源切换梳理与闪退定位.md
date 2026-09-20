# javchu.com 数据源切换梳理 · 与上游对比 · 闪退定位

> 2026-09-17 取证。结论均有代码行号或实测日志支撑；标注「嫌疑」的部分是尚未拿到 Android 崩溃栈的推断，已给出定案方法。
> 对比对象：`E:\hanime1\references\Han1meViewer-main`（下称「上游」）；本仓库 = `E:\LoveHan1me`。

---

## 0. TL;DR

1. **切到 javchu 现在只有一条活路**：设置 → 网络设置 → 网域下拉。
   `:app` 里那套「切换站点（AV ↔ 番剧）」开关**已经是死代码** —— 上游挂在**抽屉头部**，本仓库 P2 退役抽屉时把入口丢了，但 MainActivity 的状态、`confirmSiteSwitch`、对话框、字符串全留着。
2. **AV 数据侧没问题**：`Parser` / 首页分类 / 搜索词典 / 详情页词典与上游是 1:1 等价移植。已用**真实 javchu 首页 + 隔离 profile 实测桌面端跑通**（HTTP 200、完整 AV 首页 306KB、首帧 8015ms、零崩溃）。
3. **「闪退」最可能不在数据/解析，而在切换后的「重启动作」**：
   `restartApp(killProcess = true)` = `startActivity(launchIntent)` **紧接着** `exitProcess(0)`。Android 上是"把新实例起在同一个进程里、随即把自己杀掉"；桌面端更直接——**只退进程不重启**。这正好解释"再启动/再构建一次就一切正常，而且显示的是 javchu 数据"。
4. 顺手查出的两处真偏差：`selectedBaseUrl` 语义被写坏（切不回番剧站）；`AndroidManifest` 深链白名单缺 `javchu.com`（上游也缺，属继承缺陷）。

---

## 1. 现状：切到 javchu 的完整链路

### 1.1 两条切换入口，一条是死的

| 入口 | 代码 | 现状 |
| --- | --- | --- |
| **A. 「切换站点」开关**（AV ↔ 上次的番剧站） | `app/.../ui/activity/MainActivity.kt:157-170` `confirmSiteSwitch()` | **不可达（死代码）** |
| **B. 网络设置 → 网域下拉** | `shared/src/jvmMain/.../NetworkSettingsRoute.kt:244-253` + `:390-404` | 唯一活路（Android + 桌面共用） |

A 为什么是死的（可复核）：

- `MainActivity.kt:81` 把 `onSwitchSiteClick = { showSiteSwitchConfirm = true }` 传进 `MainActivityShell`；
- `AndroidShell.kt:88` 接收了该参数，但 `:94-107` 调 `AndroidOverlays(...)` 时**没有把它传下去**，`AndroidOverlays` 的签名（`:197-206`）也没有这个参数；
- 全仓库只有 `AndroidShell.kt:245` 用了 `confirm_switch_site` 字符串，`switch_site` 字符串**零引用**；
- 对照上游：入口在 `ui/screen/main/MainDrawerHeader.kt:158`（抽屉头像区的「切换站点」按钮）→ `MainActivityScaffold` → `MainActivityContent:191` → `MainActivity.kt:89`。本仓库 P2「抽屉整体退役」，这条链就断了。

副作用：`ANIME_URL`（`NetworkConstants.kt:28`）现在**只被这段死代码引用**——它是"当前站是不是番剧站"的判据，判据还活着、路已经没了。

### 1.2 B 路径做了什么（唯一活路）

```
网域下拉选中 javchu
  → onDomainChange(NetworkSettingsRoute.kt:244)  // newValue != baseUrl 才弹确认
  → 确认框 onConfirm(:390-404)
      SettingsRepository.update {
          domainName       = "https://javchu.com/"
          selectedBaseUrl  = "https://javchu.com/"     // ← 见 §2 差异 2：语义写坏
          useCustomMirrorSite / customMirrorSite / appendCustomMirrorPath = pending…
      }
      logout()                    // 清登录态 + 内存 Cookie + WebView Cookie
      restartApp(killProcess=true) // startActivity(launcherIntent) + exitProcess(0)
```

注意：**先改域名、再 logout、最后自杀式重启**，且**没有 `delay`**（上游 A 路径是 `update → delay(500) → restart`，见 `references/.../MainActivity.kt:212-221`）。

### 1.3 「当前站是不是 AV 站」的判定点（共 7 处，全部靠数组下标）

统一表达式：`SettingsRepository.baseUrl == HanimeConstants.HANIME_URL[3]`

| 文件 | 行 | 用途 |
| --- | --- | --- |
| `site/hanime1/Parser.kt` | 79 | 首页行下标 13/12 分流（`getOrNull(if (isAVSite) 13 else 12)`，行 144） |
| `feature/home/homepage/SharedHomeScreen.kt` | 79 | 传给 `HomePageContent` |
| `feature/home/homepage/HomePageMappers.kt` | 33-116 | 10 个分类的标题/筛选参数按 AV 改写 |
| `feature/search/SearchViewModel.kt` | 63 | 类型词典换 `files/search_options/genre_av.json` |
| `feature/video/VideoRouteHostScreen.kt` | 174-186 | 详情页词典同上 |
| `app/navigation/settings/HomeSettingsRoute.kt` | 557 | 首页分类设置显示 AV 标题 |
| `app/navigation/settings/SettingsRouteUtils.kt` | 17-22 | 网域下拉第 4 项 `javchu.com (av)` |

数据侧配套（都已就位）：

- 词典资源存在：`shared/src/commonMain/composeResources/files/search_options/genre_av.json`（7 项：全部/日本AV/素人業餘/高清無碼/AI解碼/國產AV/國產素人）；
- DNS/Host 白名单含 javchu：`NetworkConstants.kt:26-27`（`HANIME_HOSTNAME` / `HANIME_URL`），`HanimeDns.kt:99,178` 用它对内置 Hosts 生效；
- CF cookie 按 host 存：`HCookieJar.kt:34`（`cloudFlareCookieHost == host` 才叠加），落库侧 `CloudflareVerificationScreen.android.kt:251-262` 用**实际完成页的 host**，不会漏 javchu。

---

## 2. 与上游 `Han1meViewer-main` 的逐点对比

| # | 点 | 上游 | 本仓库 | 影响 |
| --- | --- | --- | --- | --- |
| 1 | 首页 AV 解析 | `logic/Parser.kt:58,126,130,141,153` | `site/hanime1/Parser.kt:79,144,148…` | **等价**。唯一实现差异：banner 注释里找 vcode，上游用 Jsoup `traverse(Comment)`，本仓库用正则扫 `outerHtml()`（ksoup 无 traverse），语义相同 |
| 2 | 首页分类改写 | `HomePageMappers.buildCategoryList` | 同名同表 | **等价**（含 AV 下 `genre/sort/tags` 的 10 组改写） |
| 3 | 词典资源 | `assets/search_options/genre_av.json` | `composeResources/files/search_options/genre_av.json` | 等价 |
| 4 | 详情页词典 | `VideoRouteHostScreen.kt:143` | `VideoRouteHostScreen.kt:179` | 等价 |
| 5 | 首页分类设置标题 | `HomeSettingsRoute.kt:538` | `HomeSettingsRoute.kt:557` | 等价 |
| 6 | 网域下拉列表 | `SettingsRouteUtils.kt:26-29` | `SettingsRouteUtils.kt:18-21` | 等价（4 项含 `javchu.com (av)`） |
| 7 | **切换入口** | 抽屉头部按钮 → 确认框 → `confirmSiteSwitch` | **入口缺失**（抽屉退役），`confirmSiteSwitch` 成死代码 | 见 §1.1 |
| 8 | **`selectedBaseUrl` 语义** | `if (currentSite in ANIME_URL) copy(selectedBaseUrl = currentSite, domainName = avSite) else copy(domainName = selectedBaseUrl)` —— 只在"从番剧站去 AV 站"时记住旧站 | B 路径**无条件**把 `selectedBaseUrl` 覆写成"刚选中的站" | **真偏差**：一旦从设置切到 javchu，`selectedBaseUrl` 也变成 javchu；任何按上游语义"切回上一站"的逻辑都会原地打转（`currentSite !in ANIME_URL → domainName = selectedBaseUrl = javchu`）。当前因入口已死没暴露，但**语义已坏**，恢复入口前必须先修 |
| 9 | 重启前延时 | `delay(500)` 再 restart | 无 delay，`update → logout → restartApp` | 见 §4 嫌疑 1 |
| 10 | 切换时的登录态 | 只 restart，不 logout | **多一次 `logout()`**（清登录 + 内存 Cookie + WebView Cookie） | 行为差异：切站后 CF clearance（`cf_cookie_host=hanime1.me`）与登录态都失效；对 javchu 首次请求可能直接吃 CF 挑战（见 §4 嫌疑 2） |
| 11 | 深链白名单 | `AndroidManifest.xml:69-75`：hanime1.com / hanime1.me / hanimeone.me（**无 javchu**） | 同上游，**无 javchu** | 继承缺陷：javchu 的 `/watch?v=` 分享链接打不开 App，而 `videoUrlRegex`（`HanimeUrlRegex.kt:7-9`）明明支持 javchu |
| 12 | 站点契约 | 无 | 新增 `site/SiteId|SiteConfig|SiteCatalog`（`SiteCatalog.kt` 里 javchu 只是 `Hanime1.baseUrls` 的第 4 项），但**未接管**任何判定 | 现状：javchu 靠 `HANIME_URL[3]` 下标判定。任何 URL 形态漂移（尾斜杠有无、自定义镜像 `useCustomMirrorSite`）都会让 `isAVSite` **静默失效**（退化成番剧站逻辑），这是 M6 明确要还的债 |

---

## 3. 实证：桌面端切到 javchu **不崩**（数据侧排除）

方法（不动真实 `~/.lovehan1me`）：

1. `javchu.com` 直连探活：`HTTP/1.1 200 OK`，`server: cloudflare`，**无 CF 挑战**；真实首页 313KB；
   - `#home-rows-wrapper` ×1、`horizontal-card` ×210、**行数正好 14（下标 0..13）** ⇒ 与 `Parser` 的固定下标映射（AV 取 13）吻合；
2. 复制真实 DataStore 到隔离目录，把 `domain_name` / `selectedBaseUrl` 等长替换为 `https://javchu.com/`（两者都是 19 字节），用 `-Duser.home=<隔离目录>` 启动桌面端（classpath 取自 `_appcmd2.txt` 的 jcmd dump，无需 Gradle）；
3. 结果：

```
[NetworkRequest]https://javchu.com/
[CookieString]toCookieList: [user_lang=zhs; domain=javchu.com; path=/]
[HCookieJar]loadForRequest for javchu.com: [...]
[userInfo]name:null;id:
[Startup]application+4310ms | datastore+6878ms | settings+6880ms | first-frame+8015ms | 合计 8015ms
```

- 进程存活 60s+（被我的超时终止），**无未捕获异常**；
- 缓存条目解压后确认是**完整 AV 首页**（`home-rows-wrapper`×1、`horizontal-card`×210、title=「Javchu.com - 免費高清AV/成人色情A片/在線看」）；
- 桌面崩溃报告 `%TEMP%/han1me_crash_report.txt`（`CrashRecovery.jvm.kt:7-14`）**不存在** ⇒ 从未有过未捕获异常。

**结论：Ktor/DNS/CF/解析/AV 分支/首页分类渲染，这条数据链路是好的。崩溃只可能发生在 Android 独有环节。**

---

## 4. 闪退定位（按可能性排序）

### 嫌疑 1（头号）：重启动作 = 「起新实例 + 立刻杀进程」

```kotlin
// shared/src/androidMain/.../AppRestart.android.kt:7-19
val context = Han1meDatabaseContext.appContext
context.packageManager.getLaunchIntentForPackage(context.packageName)
    ?.addFlags(CLEAR_TOP or CLEAR_TASK or NEW_TASK)
    ?.let(context::startActivity)
if (killProcess) exitProcess(0)          // ← 同一进程：新 Activity 刚被创建就被自己杀掉
```

```kotlin
// shared/src/desktopMain/.../AppRestart.desktop.kt:6-8
actual fun restartApp(killProcess: Boolean) { if (killProcess) exitProcess(0) }  // 桌面 = 直接退
```

- Android 上 `startActivity` 是**异步**的：新 Activity 通常仍在**同一个进程**里被创建，`exitProcess(0)` 紧接着执行 ⇒ 用户看到的就是"应用起来一下/直接消失"⇒ 体感「闪退」。若进程死在 AMS 处理 launch 之前，表现更彻底；
- 桌面端更直接：**只退进程、不重启**（设置页文案 `domain_change_tips` 明说"修改网域需要重新启动程式"，所以窗口消失是"设计如此"）；
- 关键佐证：**这条路径只在"切网域"时走到**（A 路径已死）。用户此前没切过网域，所以这条"自杀式重启"从没被踩过 ⇒ 完全符合"一切到 javchu 就闪退、之后再启动/再构建就正常且是 javchu 数据"；
- 上游同款实现（`utils/ActivityManager.kt:13-21`），但上游 A 路径有 `delay(500)`，本仓库 B 路径没有。

**判定方法（一锤定音）**：Android 真·未捕获异常会弹 `CrashActivity` 崩溃页（`app/.../crash/CrashHandler.kt`）。**没弹页而直接消失 ⇒ 进程是被杀不是崩的 ⇒ 就是这条。**

### 嫌疑 2：Android 独有的 CF 拦截器 + 切站后无 clearance

- `CloudflareInterceptor`（`shared/src/androidMain/.../interceptor/CloudflareInterceptor.kt:28-38`）只在 `403 + cf-mitigated: challenge` 时触发，**桌面端返回 null 不装**（`NetworkPlatform.desktop.kt:20`）⇒ 这是纯 Android 差异；
- `logout()` 顺手清了内存 Cookie 与 WebView Cookie，而 DataStore 里的 `cf_cookie_host` 仍是 `hanime1.me` ⇒ **javchu 首屏请求没有任何 clearance**，一旦被挑战就进 `CloudflareVerificationCoordinator.verify`（`CountDownLatch` 阻塞网络线程最长 5 分钟）；
- 表现应是"卡住/走异常分支"而非闪退，但**必须与嫌疑 1 一起看**：`CloudflareVerificationCoordinator` 会以 `NEW_TASK|SINGLE_TOP` **再拉一次 MainActivity**，与重启动作叠加时更容易出"起一下就没"的观感。

### 嫌疑 3：`logout()` 的位置（Android 独有实现）

`clearWebCookies()`（WebView `CookieManager`）只在 Android 有实际动作；顺序是"先改域名 → 再 logout → 再重启"，即**用新域名状态去清 cookie**。不必然崩，但语义上应该在 `update` 之前清，或者干脆不进 B 路径（对齐上游"切站不登出"）。

### 已排除

- 数据/解析/AV 分支（§3 实测）；
- DNS 与内置 Hosts（javchu 已在 `HANIME_HOSTNAME`）；
- CF cookie 落库 host 判定（按实际 host 写，不写死 hanime1）；
- `genre_av.json` 缺失（资源在、`decodeComposeAsset` 还有 `runCatching` 兜底）；
- 桌面端（实测不崩）。

### 拿到崩溃栈的三条命令（有设备时）

```bash
adb logcat -b crash -d                       # 崩溃缓冲区
adb logcat -s AndroidRuntime:E LoveHan1me:V  # 未捕获异常
adb shell dumpsys activity exit-info me.lovehan1me   # 退出原因（Android 11+）
adb shell ls /data/tombstones                # native 崩（真·闪退无页面时优先看这个）
```

桌面端若"真崩"过，会在 `%TEMP%\han1me_crash_report.txt` 留报告并在下次启动显示崩溃页 —— **该文件不存在，说明桌面端从未崩过**。

---

## 5. 建议修法

**P0-1 重启收口**（治闪退）

- Android：不要 `startActivity` 后立刻 `exitProcess`。二选一：
  - 把退出推迟到新实例起来之后（`Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 300~500ms)`），或
  - 只杀自己、让系统按记录重启：`Process.killProcess(Process.myPid())` 前不要 `exitProcess(0)`；更稳的做法是**先杀进程再由 launcher 拉起**（用户手点）。
- 与上游对齐：restart 前补 `delay(500)`（`MainActivity.confirmSiteSwitch` 里本来就有，B 路径漏了）。
- 桌面端建议保持"退出进程"语义，但把确认框文案里的"重启"改成"退出后手动重新打开"，避免被误读成崩溃。

**P0-2 `selectedBaseUrl` 语义归位**（`NetworkSettingsRoute.kt:392-400`）
按上游 `MainActivity.kt:163-166` 的写法收敛：只有当 `旧 baseUrl in ANIME_URL` 时才把旧值写进 `selectedBaseUrl`，否则保持原值。否则恢复「切换站点」入口后会出现"切不回番剧站"。

**P1-1 「切换站点」入口二选一**（`AndroidShell.kt:88` / `MainActivity.kt:81`）
要么在账号页/设置页补回按钮（把 A 路径救活，并把 B 路径的域切换与它合并），要么**删干净**：`onSwitchSiteClick` 参数、`showSiteSwitchConfirm` 状态、`AndroidOverlays` 的 `ConfirmDialog`、`switch_site`/`confirm_switch_site` 两套字符串（composeResources + app/res 四个 locale）。现在这种"半死"状态最坏：看起来支持切站，实际只有一条会走坏 `selectedBaseUrl` 的路。

**P1-2 `AndroidManifest.xml:69-76` 深链补 `javchu.com`**（顺手把 `hanimeone.me` 的现有项一起复核）。

**P1-3 `isAVSite` 判定收口**（M6 已规划）
不要再用 `HANIME_URL[3]` 下标。最小改动：在 `SiteCatalog`/`SiteConfig` 上加 `isAvSite: Boolean`（或 `SiteId.Hanime1Av`），7 个判定点统一走它；同时把 `baseUrl` 归一化（尾斜杠、大小写、自定义镜像）后再判定，避免静默退化成番剧站逻辑。

**P2 回归测试**
`shared/src/desktopTest/` 加一条"真实 javchu 首页快照解析"用例（对标上游 `JavchuRealSnapshotParsingTest`）：用 `InMemorySettingsStore`（见 `AccountChainTest.kt:53-64`）把 `domainName` 设成 javchu，断言 `homePageVer2` 走 AV 分支、14 行映射、分类标题为 AV 文案。本次已存真实首页快照：`.workbuddy/_javchu_home.html`（313KB）。

---

## 附：本次取证留下的文件

| 路径 | 说明 |
| --- | --- |
| `.workbuddy/_javchu_home.html` | javchu 首页真实快照（curl，313KB，可直接做 fixture） |
| `.workbuddy/_javchu_rows.py` | 列出 `#home-rows-wrapper` 直接子行与首行文本（本次确认 14 行） |
| `.workbuddy/_mk_av_profile.py` | 生成隔离 profile（等长替换 `hanime1.me`→`javchu.com`），不动真实 DataStore |
| `.workbuddy/_run_av.py` / `_run_av_fg.py` | 用 jcmd classpath + `-Duser.home=<隔离目录>` 启动桌面端（无需 Gradle），前者后台、后者前台带输出 |
| `.workbuddy/_av_run.log` | 后台启动的日志落点（本次为空，以后台脚本启动时用） |
