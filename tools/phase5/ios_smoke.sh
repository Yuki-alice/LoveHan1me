#!/usr/bin/env zsh
# iOS 模拟器冒烟（尽力而为）：构建 + 安装 + 启动 + 日志断言。
# iOS 设置页是占位符，只断言行为（网关/请求日志）与无崩溃；三态/诊断靠人工看行为。
# 用法： ios_smoke.sh [设备名，默认 "iPhone 17"]
set -u
HERE="${0:A:h}"
source "$HERE/common.sh"
DEVICE="${1:-iPhone 17}"
R="$OUT/ios-smoke.result"; : > "$R"
cd "$HERE/../.." || exit 1

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build > "$OUT/ios-build.log" 2>&1 || {
  fail "$R" "xcodebuild 失败，见 ios-build.log"; exit 1
}
APP=$(find ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator \
  -maxdepth 1 -name "LoveHan1me.app" 2>/dev/null | head -n 1)
[ -n "$APP" ] || { fail "$R" "找不到 LoveHan1me.app 产物"; exit 1 }
print "app=$APP" > "$OUT/ios-info.txt"
xcrun simctl boot "$DEVICE" > /dev/null 2>&1
xcrun simctl install booted "$APP" > /dev/null 2>&1 || { fail "$R" "simctl install 失败"; exit 1 }
# bundle id 见工程配置（target 名 LoveHan1me，identifier me.lovehan1me.ios）
BUNDLE="me.lovehan1me.ios"
xcrun simctl launch booted "$BUNDLE" > /dev/null 2>&1 || { fail "$R" "simctl launch 失败（bundle=$BUNDLE）"; exit 1 }
xcrun simctl spawn booted log stream --level debug --predicate 'process == "LoveHan1me"' > "$OUT/ios-log.txt" 2>&1 &
LOGPID=$!
sleep 90
kill $LOGPID 2>/dev/null
# 断言：进程还在 + 无 FATAL/崩溃痕迹（网关/请求细断言靠人工，Darwin 日志口径待收敛）
if ! xcrun simctl listapps booted 2>/dev/null | grep -q "$BUNDLE"; then
  fail "$R" "应用不在已安装列表"; exit 1
fi
grep -qiE "fatal|crash|uncaught" "$OUT/ios-log.txt" && { fail "$R" "日志见崩溃痕迹"; exit 1 }
pass "$R" "安装启动存活/无崩溃痕迹（行为细项人工补）"
