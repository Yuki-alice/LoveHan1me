#!/bin/sh
# 构建 iOS 网关运行时（gomobile xcframework），输出到 iosApp/libs/ 随包分发。
#
# 前置：Go（`go install golang.org/x/mobile/cmd/gomobile@latest`，本仓库
# echgate/go.mod 已声明 gobind 工具依赖）+ Xcode。
# 只绑 gate 包（StartFlat 扁平入口；Config 的 struct/[]string 形态
# gobind 不支持，见 gate/mobile.go）。
set -eu
cd "$(dirname "$0")"
export PATH="$PATH:$(go env GOPATH)/bin"
OUT="../iosApp/libs/Echgate.xcframework"
rm -rf "$OUT"
gomobile bind -target ios -o "$OUT" lovehan1me/echgate/gate
echo "==> $OUT"
du -sh "$OUT"
