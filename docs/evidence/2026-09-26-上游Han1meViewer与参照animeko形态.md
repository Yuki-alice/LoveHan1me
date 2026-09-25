# 上游 Han1meViewer 与参照项目 animeko 的外部形态

## Han1meViewer（daisukiKaffuChino）是只做 Android 的 Kotlin 客户端，仍在维护

- 取证日：2026-09-26
- 重跑：`curl -sL https://raw.githubusercontent.com/daisukiKaffuChino/Han1meViewer/main/README.md`
- 环境：macOS（darwin），可直连 github.com
- 失效条件：上游 README 改版、仓库归档或转为私有

README 原话：「Han1meViewer 是一个使用 Kotlin 开发的 Android 客户端，用于浏览、搜索、播放和管理
hanime 相关公开视频页面内容」。技术栈为 Jetpack Compose、Material 3、Navigation 3、ViewModel、
StateFlow、Retrofit、Jsoup、Room、DataStore、WorkManager、Media3/MPV。

功能面：视频浏览、详情播放、搜索、用户列表、下载管理、评论、订阅、设置、隐私保护。
另有平板/横屏布局与 Google Cast 投屏——**都在 Android 之内，不构成第二或第三端**。

维护状态：原仓库 `misaka10032w/Han1meViewer` 已归档，现由 daisukiKaffuChino 接手维护；
取证时最近的提交在 2026-08。

许可：整体按 GPLv3 发布，含来自 MomoQR 的 GPLv3 代码与 Yenaly 遗留的 Apache-2.0 部分。

上游另有两条对外姿态声明，本仓库沿用：
「本软件不接受任何形式的公开宣传。若出现公开宣传、搬运或引流，仓库维护者可能随时归档或隐藏仓库」；
「GitHub Release 是唯一下载及更新渠道」。

## animeko 是 Android/iOS/Windows/macOS/Linux 五端的 Kotlin + Compose Multiplatform 项目

- 取证日：2026-09-26
- 重跑：`curl -sL https://raw.githubusercontent.com/open-ani/animeko/main/README.md`
- 环境：macOS（darwin），可直连 github.com
- 失效条件：上游 README 改版、仓库归档或转为私有

README 原话：「Animeko 支持所有主流平台：Android、iOS、Windows、macOS、Linux」，
「100% Kotlin/Compose Multiplatform」。

其中与本项目相关的一句是播放器部分：「适配多平台的视频播放器，Android 底层为 ExoPlayer，
PC 底层为 VLC」——即**一套 UI 与抽象、各端接各自内核**的跨平台视频模块形态。
本仓库落这一形态时用的抽象层是 mediamp（NOTICE 有完整归属），内核选择为
Android/ExoPlayer、桌面/mpv、iOS/AVKit；桌面选 mpv 而非 VLC 的原因记在
`docs/decisions.md`。

许可：AGPLv3。本仓库 NOTICE 明确声明未包含 animeko 源码，仅作架构参考。
