#!/bin/sh
# 构建 ECH 网关各 OS 产物，输出到 desktopApp/src/main/resources/ 随包分发。
#
# 命名：echgate-<os>-<arch>[.exe]，由 EchGateProcess 按运行平台挑选。
# 只入库源码（见 .gitignore 注释）：resources 下的二进制随仓库走、
# echgate/ 下的中间产物不提交。
set -eu
cd "$(dirname "$0")"
OUT="../desktopApp/src/main/resources"
mkdir -p "$OUT"

build_one() {
  os="$1"; arch="$2"; out="$3"
  echo "==> $os/$arch -> $out"
  GOOS="$os" GOARCH="$arch" CGO_ENABLED=0 go build -trimpath -o "$OUT/$out" .
}

# Windows（历史默认名 echgate.exe 保留一份，兼容已 dispersion 的旧包与 live 测试查找路径）
build_one windows amd64 "echgate-windows-amd64.exe"
cp "$OUT/echgate-windows-amd64.exe" "$OUT/echgate.exe"

build_one darwin arm64 "echgate-darwin-arm64"
build_one darwin amd64 "echgate-darwin-amd64"
build_one linux amd64 "echgate-linux-amd64"

ls -la "$OUT" | grep echgate
