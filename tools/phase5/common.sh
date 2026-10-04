#!/usr/bin/env zsh
# Phase 5 共享环境与断言。只被 source，不直接执行。
# 约定：每个脚本把结果写进 $OUT/<name>.result（KEY=VALUE），日志与产物同目录。

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ADB="$ANDROID_HOME/platform-tools/adb"
export EMULATOR="$ANDROID_HOME/emulator/emulator"
export PKG="me.lovehan1me.debug"
export ACTIVITY="lovehan1me.ui.activity.MainActivity"
export OUT="${PHASE5_OUT:-/tmp/phase5}"
mkdir -p "$OUT"

pass() { print "STATUS=PASS" >> "$1"; print "DETAIL=$2" >> "$1" }
fail() { print "STATUS=FAIL" >> "$1"; print "DETAIL=$2" >> "$1" }

# $1=avd名；模拟器没跑就起，轮询到 device online + boot 完成（最多 $2 秒，默认 300）。
# 不用 adb wait-for-device（无超时，会永久卡住）也不用 setsid（macOS 没有）。
ensure_emu() {
  local avd="$1" timeout="${2:-300}" waited=0
  if ! "$ADB" get-state 1>/dev/null 2>&1; then
    nohup "$EMULATOR" -avd "$avd" -no-window -no-audio -no-boot-anim \
      -no-snapshot -gpu swiftshader_indirect -no-metrics > "$OUT/emu-boot.log" 2>&1 &!
    sleep 10
  fi
  while [ "$waited" -lt "$timeout" ]; do
    if "$ADB" get-state 1>/dev/null 2>&1; then
      if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        return 0
      fi
    fi
    sleep 5; waited=$((waited + 5))
  done
  return 1
}

# $1=log文件 $2=模式 $3=超时秒；出现即 0，超时 1。
wait_log() {
  local log="$1" pattern="$2" timeout="$3" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    grep -qE "$pattern" "$log" 2>/dev/null && return 0
    sleep 3; waited=$((waited + 3))
  done
  return 1
}
