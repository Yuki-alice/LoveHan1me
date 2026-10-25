# LoveHan1me v0.0.1 · 首个公开预览版

> 🔞 **R18：未满 18 岁禁止下载和使用。** 本应用与上游站点无任何关联，自身不存放、不托管、不分发任何内容，全部数据实时来自第三方站点的公开页面。请勿在公开平台宣传本软件。

Kotlin Multiplatform 从零重写的跨平台第三方客户端：**一套代码，五端运行**。

## 本版包含

- **播放**：Media3 / mpv / AVKit 三端统一播放器界面；Anime4K 超分、画中画、帧预览、倍速、记忆续播、完整手势
- **弹幕**：自研引擎（轨道分配 / 重叠规避 / 帧平滑），弹弹play + 站内评论双来源
- **下载**：并发闸门、断点续传、暂停恢复、分组管理、限速；本地播放离线秒播
- **网络与反封锁**：内建 ECH 网关隐藏 SNI（Go 三端原生库）、DoH、代理规则分流、每域出口覆盖、自动镜像切换、Cloudflare 验证窗
- **界面**：M3 Expressive、动态取色、深浅色、三语（简中/繁中/EN）、应用锁、整机备份

完整变更与已知限制见 [CHANGELOG](https://github.com/Yuki-alice/LoveHan1me/blob/main/CHANGELOG.md)。

## 下载

| 附件 | 平台 |
|---|---|
| `LoveHan1me-v0.0.1.apk` | Android 10+（arm64-v8a） |
| `LoveHan1me-1.0.0.dmg` | macOS（Apple Silicon / Intel jlink 运行时） |

**iOS**：本版不发布 TestFlight/商店包；从源码构建（`cd iosApp && xcodegen generate`，需 macOS + Xcode）。
**Windows / Linux**：本版暂未附带安装包，从源码构建 `./gradlew :desktopApp:packageDistributionForCurrentOS`（Msi / Deb）。后续版本补齐跨平台产物。

## 已知限制（首发如实声明）

- iOS 的自定义 DNS / 自定义 Hosts 跟随系统设置（应用内不可配）
- iOS 应用内 SOCKS 代理未经真机充分验证（HTTP 为主用法）；`Direct` 档能否真正关闭系统代理未真机验证
- 部分网络能力标注「待真机/受限网络验收」，完整清单见仓库 `docs/plan/`（B0–B5 台账与活计划）
- 若上游站点无法访问（被墙且网关不可用），应用同样无法访问 —— 网络能力不是翻墙承诺

## 反馈

- Issue：https://github.com/Yuki-alice/LoveHan1me/issues
- 本项目仅供学习与技术研究，使用前请完整阅读 [免责声明](https://github.com/Yuki-alice/LoveHan1me#-免责声明)。
