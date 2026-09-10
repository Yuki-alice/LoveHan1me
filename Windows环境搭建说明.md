# Windows 环境搭建与编译说明

> 记录日期：2026-09-10
> 适用：在 `E:\LoveHan1me` 用 Windows 构建 KMP 三端项目
> 背景：项目源码从 Mac（Apple Silicon）迁移而来，**Mac 上可正常编译**；以下全部是 Windows 侧的环境差异与坑

---

## 一、环境实况（本机）

| 项 | 路径 / 值 | 说明 |
|---|---|---|
| Gradle | 9.4.1（wrapper，走腾讯云镜像） | `gradle/wrapper/gradle-wrapper.properties` |
| JDK 21 | `D:\DevCache\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2` | JBR 21.0.11；已写入 `gradle.properties` 的 `org.gradle.java.home` |
| 系统 JAVA_HOME | `C:\Program Files\Java\jdk-17` | **版本不足**，必须由上一项覆盖 |
| Android SDK | `D:\DevCache\Android\Sdk` | 已写入 `local.properties` 的 `sdk.dir` |
| Android platforms | **仅 android-36** | 项目 `compileSdk = 37`（见坑 4） |
| Android build-tools | 36.0.0 / 36.1.0 / 37.0.0 | 齐全 |
| Gradle 依赖缓存 | `D:\DevCache\.gradle`（约 4.6G） | 系统默认 `GRADLE_USER_HOME` |

---

## 二、编译命令

### 用户在终端 / IDE 里跑（推荐）

```bash
cd /e/LoveHan1me
./gradlew :shared:compileKotlinDesktop
```

### Agent（自动化）跑

沙箱不允许 Gradle 子进程写工作区外的路径，必须把 `GRADLE_USER_HOME` 指向工作区内：

```bash
./gradlew -g E:/LoveHan1me/.gradle-home :shared:compileKotlinDesktop
```

> `E:\LoveHan1me\.gradle-home` 是从 `D:\DevCache\.gradle` 复制来的完整缓存（约 6G），已在 `.gitignore` 中排除。

---

## 三、已解决的坑（按踩坑顺序）

### 坑 1：`journal-1.lock` AccessDenied

**现象**：`FileNotFoundException: ...\caches\journal-1\journal-1.lock (拒绝访问)`，构建 8 秒即失败。

**原因**：别的 Gradle daemon 独占该锁文件。本机曾是被废弃项目 `E:\hanime1` 的 8 个 java 进程占用。

**解法**：找到并停止占用进程。

```bash
# 查看 java 进程命令行，确认归属
powershell -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Select ProcessId,CommandLine"
# 停止自己的 daemon
./gradlew --stop
```

**注意**：`wmic` 已被本机安全策略列入黑名单，不可使用。

### 坑 2：NDK 下载卡死（**最耗时，24 分钟**）

**现象**：构建长时间无输出，`shared/build` 目录根本没创建，依赖缓存 6 秒增长 0M。日志末行停在：
```
Preparing "Install NDK (Side by side) 28.2.13676358"
```

**原因**：`app/build.gradle.kts` 声明了 `externalNativeBuild { cmake { ... } }`，AGP 在配置 `:app` 时触发 NDK 下载（数百 MB），国内网络挂起。

**解法**：**砍掉**。`app/src/main/cpp/`（`chino.cpp` / `kaffu.c`）经核查是**死代码**——全仓无任何 `System.loadLibrary` 引用（grep 命中的只是包名 `daisukikaffuchino` 恰好含 "kaffu"）。

```kotlin
// app/build.gradle.kts 中删除两处：
//   1) defaultConfig 内的 externalNativeBuild { cmake { cppFlags(...); abiFilters(...) } }
//   2) 顶层的 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
```
并删除 `app/src/main/cpp/` 目录。

### 坑 3：依赖仓库零国内镜像

**现象**：依赖解析极慢。

**原因**：`settings.gradle.kts` 只声明了 `google()` / `mavenCentral()` / `jitpack.io` / **`jogamp.org`**，无任何国内镜像。而 `jogamp.org` 在国内基本不可达（它是 KCEF/JCEF 的 JOGL 唯一发布源）。

**解法**：加入阿里云 / 腾讯云 / 华为云镜像，并把 `jogamp.org` **移到最后**（前置会让解析挂起）。

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
google(); mavenCentral(); maven { url = uri("https://jitpack.io/") }
maven { url = uri("https://jogamp.org/deployment/maven/") }  // 必须最后
```

### 坑 4：Android platform-37 缺失

**现状**：SDK 只有 `platforms/android-36`，项目 `compileSdk = 37`。

**状态**：尚未确认是否真阻塞（第 5 次构建已通过配置阶段，未就 SDK 报错，AGP 可能自行处理）。

**若报错**，两条路：
1. 用 sdkmanager 安装（但本机 **无 cmdline-tools**）
2. 临时把 `gradle.properties` 的 `han1me.android.compileSdk=37` 改为 `36`

### 坑 5：`kotlin-dsl` accessors 删除被沙箱拒绝（**最隐蔽**）

**现象**：
```
A problem occurred configuring project ':shared'.
> java.util.concurrent.TimeoutException
> The Room Gradle plugin was applied but no schema location was specified.
```
**但** `shared/build.gradle.kts:29-31` 明明已有 `room { schemaDirectory("$projectDir/schemas") }`。

**原因**：stderr 显示沙箱拒绝了
```
D:\DevCache\.gradle\caches\9.4.1\kotlin-dsl\accessors\...\Accessors*.kt (读/删 · 拒绝)
```
Gradle 需要**删除并重新生成 Kotlin DSL accessors**，删除被拒 → 超时 → `:shared` 配置中断 → `room {}` 块未执行 → 抛出误导性的 Room 报错。

**关键事实**：沙箱限制的是 **Gradle 的 java 子进程**（shell 本身可自由读写删 D/E 盘文件）。`dangerouslyDisableSandbox` 也未能解除。

**解法**：`GRADLE_USER_HOME` 移到工作区内 → `./gradlew -g E:/LoveHan1me/.gradle-home ...`（见第二节）。

**辅助**：手动创建 `shared/schemas` 目录（Room `schemaDirectory` 的目标）。

### 坑 6：git 操作被 SIGTERM

**现象**：`git add -A` / `git commit` 被 SIGTERM 中断，但实际工作可能已完成。

**解法**：
- 预先 `git config core.autocrlf false`（避免 CRLF warning 刷屏把输出撑爆）
- 命令输出重定向到 `/dev/null`，只回显结果
- 若残留 `index.lock`，直接 `rm -f .git/index.lock` 重试

---

## 四、与 Mac 的差异（编译产物或运行时）

| 层面 | macOS | Windows | 影响 |
|---|---|---|---|
| 中文字体 | PingFang SC | 微软雅黑 | **字体度量不同 → 固定 dp 布局会位移/溢出** |
| 渲染后端 | Skiko → Metal | Skiko → Direct3D | 阴影/模糊/渐变细微差异 |
| 视频硬解 | VideoToolbox | D3D11VA / NVDEC | 播放性能、功耗、格式支持 |
| 滚动 | 惯性 + 触控板手势 | 滚轮步进 | 手感不同 |
| AWT 全屏 | `setFullScreenWindow` | 同 API | 行为有差异 |

**项目特有风险**：commonMain 有 **972 处硬编码 dp** + 桌面窗口固定 `DpSize(480.dp, 800.dp)`（`desktopApp/.../Main.kt:77`），字体度量差异会被放大。

**对策留到阶段二 P2 设计系统**：① 打包统一中文字体进 `composeResources`；② 固定 dp 改 `wrapContent`/`weight`；③ 桌面端用已有 `ui/adaptive/{Breakpoints, WindowSize}` 做响应式。

---

## 五、快速自检清单

```bash
# 1. JDK 21 生效？
./gradlew --version        # Launcher JVM 可能是 17，但 Daemon JVM 应为 21

# 2. Android SDK 就位？
cat local.properties       # 应含 sdk.dir=D:\\DevCache\\Android\\Sdk

# 3. 有没有别的 daemon 占锁？
./gradlew --stop

# 4. 缓存是否在增长（判断是否真在下载）
du -sm "$GRADLE_USER_HOME/caches/modules-2"

# 5. 构建产物是否出现（判断是否进入真编译）
ls shared/build            # 出现 classes/ 说明已进入 Kotlin 编译
```
