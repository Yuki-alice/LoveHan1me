#!/bin/sh
# 构建 Android 网关运行时（gomobile AAR），输出到 app/libs/ 随仓库分发。
#
# 前置：Go + gomobile（`go install golang.org/x/mobile/cmd/gomobile@<与 go.mod 同版本>`）
#      + Android NDK（ANDROID_NDK_HOME，或 ANDROID_HOME/ndk/<ver>）。
# 只绑 gate 包（StartFlat 扁平入口；Config 的 struct/[]string 形态 gobind 不支持，
# 见 gate/mobile.go），与 iOS xcframework 共用同一份 gate.Start。
#
# ABI 只编 arm64-v8a（发布必需）与 x86_64（模拟器调试）；发布包 ABI splits
# 已限定 arm64-v8a（app/build.gradle.kts），故这里多编的 x86_64 只进测试包。
#
# 产物入库后使用端不需要 Go/NDK 工具链（见 .gitignore 注释：只入库源码，
# 二进制随仓库走）。D2 要求与 iOS 产物来自同一 commit，脚本把两个指纹打到
# stdout 供抄录进 docs/evidence/。
#
# gomobile 会顺带产出 `<名字>-sources.jar`（绑定层的 Java 源码）——它与 AAR 是
# 一对，**一起入库**：IDE 能据此给 AAR 做 source attach，读 `gate.Gate.startFlat`
# 的签名不必再解包。`.gitignore` 不拦 `app/libs/`，两者都随仓库走。
set -eu
cd "$(dirname "$0")"
export PATH="$PATH:$(go env GOPATH)/bin"

# gomobile 的 android 目标需要 NDK；Android Studio 装的 SDK 里通常已有最新一份。
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
    [ -n "$sdk" ] && [ -d "$sdk/ndk" ] || continue
    latest=$(ls -1 "$sdk/ndk" | sort -V | tail -1)
    [ -n "$latest" ] && export ANDROID_NDK_HOME="$sdk/ndk/$latest" && break
  done
fi
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "错误：找不到 Android NDK（设 ANDROID_NDK_HOME 或 ANDROID_HOME）" >&2
  exit 1
fi
echo "==> NDK: $ANDROID_NDK_HOME"

OUT="../app/libs/Echgate.aar"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"

# -androidapi 29 = minSdk（gradle.properties han1me.android.minSdk），不要抬高：
# 抬到更高只会让 gobind 生成的类调用不到更低 API 的运行时。
gomobile bind \
  -target=android/arm64,android/amd64 \
  -androidapi 29 \
  -o "$OUT" \
  lovehan1me/echgate/gate

echo "==> $OUT"
ls -l "$OUT"
SOURCES="${OUT%.aar}-sources.jar"
[ -f "$SOURCES" ] && echo "==> $SOURCES（与 AAR 成对入库，见头部注释）"
echo "==> sha256(aar):     $(shasum -a 256 "$OUT" | awk '{print $1}')"
[ -f "$SOURCES" ] && echo "==> sha256(sources): $(shasum -a 256 "$SOURCES" | awk '{print $1}')"
echo "==> go: $(go version)"
