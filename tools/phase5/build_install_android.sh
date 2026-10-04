#!/usr/bin/env zsh
# Android 构建+安装：debug 通包（双 ABI，splits 只在 Release 生效）。
# 产物信息记进 $OUT/build-info.txt；安装失败直接 FAIL。
# 用法： build_install_android.sh [avd名，默认 han1me-16kb]
set -u
HERE="${0:A:h}"
source "$HERE/common.sh"
AVD="${1:-han1me-16kb}"
cd "$HERE/../.." || exit 1
R="$OUT/build-install.result"; : > "$R"

ensure_emu "$AVD" || { fail "$R" "模拟器未上线"; exit 1 }
./gradlew :app:assembleDebug --console=plain > "$OUT/assemble.log" 2>&1 || {
  fail "$R" "assembleDebug 失败，见 assemble.log 尾部"; exit 1
}
APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1)
[ -n "$APK" ] || { fail "$R" "debug 目录下无 APK"; exit 1 }
{
  print "apk=$APK"
  "$ANDROID_HOME/build-tools/"*/aapt dump badging "$APK" 2>/dev/null | grep -E "versionName|native-code|package" | head -n 5
  ls -la "$APK"
} > "$OUT/build-info.txt" 2>&1
"$ADB" install -r -g "$APK" > "$OUT/install.log" 2>&1 || {
  fail "$R" "adb install 失败（设备在线？$("$ADB" get-state 2>&1)），见 install.log"; exit 1
}
grep -q "Success" "$OUT/install.log" || { fail "$R" "install 未报 Success"; exit 1 }
pass "$R" "安装成功，见 build-info.txt"
