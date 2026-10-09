# app/libs 说明

本目录随仓库分发两个二进制（`.gitignore` 不拦）：

- `Echgate.aar` —— ECH 网关 Android 运行时（gomobile 进程内起服，`gate.Gate`），
  `:app` 经 `files("libs/Echgate.aar")` 消费（AGP 不允许 library 模块消费本地 .aar，
  故引用只能落在应用壳；见 `b0cad19`）。
- `Echgate-sources.jar` —— 同次构建顺带的绑定层 Java 源码，仅供 IDE source attach。

## 溯源

两者都由仓内 `echgate/` 经 `echgate/build-android.sh` 构建（只编 arm64-v8a +
x86_64），与 iOS xcframework 共用同一份 `gate.Start` 入口。`gate/` 有改动时必须重跑
脚本并一起入库，不要只提交源码不更新产物（否则运行时跑的是旧网关）。

## 不要

- 不要把其它 .aar 往这里塞：本地 .aar 无版本元数据，第二个进来就分不清谁是谁，
  到时改走版本目录或 Maven 坐标。
