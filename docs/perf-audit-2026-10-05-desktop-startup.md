# Compose Performance Audit — 2026-10-05 — desktop cold start

> 方法学声明（相对标准审计模板的偏差，如实记录）：
> 本仓库是 Compose Multiplatform 桌面端，Android Macrobenchmark /
> Baseline Profile 工具链不适用。基线与验证统一用 `StartupTrace` 埋点
> （`desktopApp` 三次冷启动实跑取数），诊断用请求分段计时 + `curl` 对照。
> 首帧≠首屏有内容——本报告以 **time-to-content**（首屏内容到达）为准，
> 而非 first-frame。

## Environment
- Kotlin 2.4.10 / CMP 1.12.0 / JDK 21 (Corretto) / macOS arm64
- 测量目标：`./gradlew :desktopApp:run`（开发构建；发行包会更快，此处数字是下限）
- Device/Network：本机不受限网（直连站点 TLS 0.3s 通）

## Baseline (Phase 1, 3 runs, before any fix)

| run | first-frame | update-check | homepage fetch→content | time-to-content |
|---|---|---|---|---|
| 1 | 2400ms | 2395→5828ms (3.4s) | 6458→12790ms (6.3s) | 12.8s |
| 2 | 2095ms | 2089→4620ms (2.5s) | 5228→11702ms (6.5s) | 11.7s |
| 3 | 1602ms | 1598→3469ms (1.9s) | 3942→10913ms (7.0s) | 10.9s |

- 首页链路是**串行三连**：更新检查 → 公告远端拉取 → 首页请求，全走网络。
- 首帧本身（1.6–2.4s，JVM 预热方差）不是病。

## Diagnosis (Phase 2)

- 更新检查 2–3.5s：单个远端小 JSON（腾讯云 COS），直连实测 1.5s；
  每次冷启动都打，且串行挡在首页之前。
- 首页 6.2–7.0s：**单次** HTTPS 请求耗时 6185ms（分段计时：body 16ms，
  parse 196ms），同 URL 直连 curl 仅 0.8s。差额在网关 ECH 路径内
  （上游拨号瞬通，6s 花在网关内 TLS/ECH 或上游 TTFB）——属 Go 网关侧课题，
  应用侧修不了这 6s，只能绕开它（见 E2）。
- 公告远端拉取挡在首页之前（update-end→fetch-start 间隙 1.7–3s），
  且观测到公告地址 404（独立问题，未在本期处理）。

## Fixes applied (Phase 3, one cause each, re-measured)

| Fix | Change | Files | Delta (time-to-content) |
|---|---|---|---|
| B1a | `DataStoreManager.initialize` 拆分：建对象同步返回，磁盘读+回填全放后台；`update()` 契约不变 | `data/datastore/DataStoreManager.kt` | Ready 1389ms → 45ms（桌面实测） |
| B1b | 删零调用 `runBlockingIo`（expect + 三端 actual） | `data/datastore/DataStorePlatform.*` | 零行为变化（死代码清除） |
| B1c | 桌面下载队列移出 Ready 关键路径 + `initializeDesktopDownloadQueue` 去 `runBlocking` 壳 | `desktop/Main.kt`，`DesktopDownloadWorkController.kt` | download-queue 首帧前 → 首帧后 |
| E1 | 更新检查 12h TTL（失败不记戳，下次重试；强制更新最多延迟一个 TTL） | `AppSettings` + `DataStoreManager` + `SettingsRepository` + `AppUpdateChecker` | 更新段 1654ms → 34ms |
| E2 | 首页 HTML stale-while-revalidate（文件缓存，key=域+用户；只读首展，csrf 等副作用不碰；写透钩子） | `HomePageCache.*`（common/jvm/ios）+ `NetworkRepo.websiteIOFlow` + `HomePageViewModel` | 内容 11.5s → 5.7s |
| E3 | 冷启动公告只读缓存，远端刷新放后台（手动下拉仍走网络版） | `HomePageViewModel` | 内容 5.7s → 2.6–4.1s |

## Verification (Phase 4)

- `:shared:desktopTest` 全量 green（496 用例；中途 `EchGateLiveTest` 挂一次，
  单跑即绿——该文件有抖动前科，评审文档亦记录过同类，非回归）；
  `:app:compileDebugKotlin` green；iOS 双架构编译 green（E2 的 iosMain 缓存实现）。
- `OrientationTest`（B-line 顺带）强制重跑 3/0。
- 桌面实跑埋点：Ready 45ms，download-queue 首帧后，内容 2.6–4.1s
  （此前 11–13s）；首帧本身 1.6–4.6s 系 JVM 预热方差，非本期路径。
- 临时诊断探针（`PerfDiag`）已删除；保留的永久埋点：
  `home-update-check-start/end`、`home-fetch-start`、`home-content-ready`。

## Open items / follow-ups

- 网关 ECH 单请求 6s（直连 0.8s）：Go 侧课题，需网关拨号/TLS 分段计时；
  应用侧已绕开（E2），但弱网下仍是 6s 级恢复时间。
- 公告地址 404：独立问题（疑似站点侧地址变更），应用自愈继续跑，未处理。
- 首帧 JVM 方差：发行包（jpackage）与本报告开发构建不可比，发布前重测。
- `DataStorePlatform.android.kt:10` 发现 `Hanime`/`Hanime`（字母 i/数字 1）
  拼写不一致致 `:app` 编译失败，已修正为真实类名；建议后续 agent 注意该文件。
