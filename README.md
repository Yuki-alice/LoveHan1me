# LoveHan1me

跨平台（Android / 桌面 / iOS）的 [hanime1.me](https://hanime1.me) 第三方客户端。
Kotlin Multiplatform + Compose Multiplatform，一套代码三端。

> ⚠️ **成人内容**：本客户端聚合的站点含色情内容。请自行确认所在地区是否允许访问与持有。
> **开发中**：尚未发布可用版本。

---

## 它是什么，不是什么

- 是**第三方客户端**：不提供、不托管、不分发任何媒体内容，所有内容来自站点本身。
- 与 hanime1.me 官方无任何关联；不代表站点立场。
- 仅供技术学习与交流，不得用于商业用途。
- 网络访问需使用者自行解决（应用内可配置代理，见下）。

---

## 环境要求

- JDK 21
- Android：Android SDK，平台 `android-37`（`minSdk 29`）
- iOS：macOS + Xcode（Kotlin/Native 只能在 macOS 上构建 iOS 目标）
- Gradle 由 wrapper 提供，不需要单独安装

## 跑起来

```bash
./gradlew :desktopApp:run      # 桌面
./gradlew :app:assembleDebug   # Android 调试包
```

iOS 工程由 xcodegen 生成，步骤见 [`iosApp/README.md`](iosApp/README.md)。

## 可选：弹幕凭据

想用自己的弹弹play 密钥，在仓库根目录的 `local.properties`（已被 gitignore）里加：

```properties
han1me.danmaku.dandan.app.id=你的 AppId
han1me.danmaku.dandan.app.secret=你的 AppSecret
```

构建时会生成到 `build/` 下，源码里永远不含真实值。设置页里的同名输入框用于覆盖它。
没有凭据时弹幕功能静默关闭，不影响播放。

---

## 文档

- 本 `README.md`：面向人，只讲是什么 / 怎么跑 / 许可。
- [`POSITIONING.md`](POSITIONING.md)：项目定位——它从哪来、为什么存在、边界在哪（给项目所有者和 agent）。
- [`AGENTS.md`](AGENTS.md)：给 AI agent 的仓库规则（也欢迎人读，但它是规则不是介绍）。
- [`docs/decisions.md`](docs/decisions.md)：产品与技术上的取舍与"明确不做"。
- [`docs/evidence/`](docs/evidence)：对外部世界（站点、第三方 API、上游库）的实测记录。

**这里没有架构说明书。** 模块划分看 `settings.gradle.kts`，具体行为读代码——
本项目刻意不维护会过期的架构描述。

---

## 许可与归属

以 **GNU GPL v3.0** 发布（[`LICENSE`](LICENSE)）。
上游归属与第三方许可（Han1meViewer / MomoQR / Anime4K shaders / mediamp / libmpv）
完整列在 [`NOTICE`](NOTICE)。
