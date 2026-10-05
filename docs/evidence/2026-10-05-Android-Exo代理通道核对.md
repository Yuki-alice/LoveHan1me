# Android 播放出口（Exo）代理通道核对

> 计划：`docs/plan/后期攻坚-网络与全端-审阅与规划.md` 阶段 4.4（F3）。
> 目的：把 `PlayerWiring.android.kt` 里"系统属性是否被 Exo 尊重**未实测**"这句坐实。
> 取证日：2026-10-05，Windows 本机（**无 Android 设备**）。

## 结论（一句话）

media3 `DefaultHttpDataSource` **没有任何 proxy 字段**，每个连接都走
`URL.openConnection()`；出口因此由 **JVM 默认 `ProxySelector`** 决定。本应用启动时已
`ProxySelector.setDefault(HanimeProxySelector())`（`app/src/main/kotlin/lovehan1me/HanimeApplication.kt:88`），
**显式代理正是从这条默认选择器生效** —— 不是靠 `HanimeProxySelector.rebuildNetwork()`
写的那几个 `http(s).proxyHost/Port` 系统属性（对 Exo 而言那是旁路）。

## 已验部分：media3 侧不设显式代理

- 依赖版本：`gradle/libs.versions.toml:44` `exoplayer = "1.10.1"`。
- 播放工厂不传代理：`video/engine/src/androidMain/.../MediampExoPlaybackEngine.kt:79`
  `DefaultHttpDataSource.Factory().setUserAgent(...).setDefaultRequestProperties(...)`，
  **没有** `.setProxy(...)`。
- 反编译取证（本机实跑，可复现）：

  ```powershell
  # 1) 从 Gradle 缓存取出 media3-datasource 的 aar 并解出 classes.jar
  $aar = (Get-ChildItem 'D:\DevCache\.gradle\caches\modules-2\files-2.1\androidx.media3\media3-datasource' -Recurse -Filter 'media3-datasource-1.10.1.aar' | Select-Object -First 1).FullName
  Expand-Archive $aar -DestinationPath <out>   # aar 即 zip，展开得 classes.jar
  # 2) 反汇编并找连接构造
  & 'D:\DevCache\.gradle\jdks\<jdk21>\bin\javap.exe' -p -c -classpath <out>\classes.jar `
      androidx.media3.datasource.DefaultHttpDataSource | Select-String 'openConnection|proxy'
  ```

- 关键输出（原样摘录）：

  ```
  private java.net.HttpURLConnection makeConnection(java.net.URL, int, byte[], long, long, boolean, boolean, java.util.Map<java.lang.String, java.lang.String>) throws java.io.IOException;
       2: invokevirtual #326  // Method openConnection:(Ljava/net/URL;)Ljava/net/HttpURLConnection;

  java.net.HttpURLConnection openConnection(java.net.URL) throws java.io.IOException;
       1: invokevirtual #424  // Method java/net/URL.openConnection:()Ljava/net/URLConnection;
  ```

  `Select-String 'proxy'`（不区分大小写）**零命中** —— 该类里没有代理字段/分支。

## 未验部分（待真机）

Android 平台 `HttpURLConnection` 是否**额外**读取 `http.proxyHost / https.proxyHost`
系统属性，本机无设备、`testAndroidHostTest` 跑不了 Android 框架，**未验**。
已验的是"media3 经默认 `ProxySelector` 取出口"这条代码链；真机回归时以抓包为准。
换网/代理设置变更后 `rebuildNetwork()` 仍写标准键（对 WebView/其它通道有用），
但**不要再把它当成"Exo 走代理"的依据**。

## 影响

- `PlayerWiring.android.kt` 该段 KDoc 已改：删掉"未实测"的悬置表述，写明上述结论与
  待真机项。`proxyUrlFor` 仍恒 `null`（Exo 不消费 URL 形式的代理，形状是给 ffmpeg 的）。
- 若将来要让 Exo 用**显式代理对象**（绕过选择器），正确做法是自建带 `Proxy` 的
  `DataSource`，而不是往 `proxyUrlFor` 塞 URL。