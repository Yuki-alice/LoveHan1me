#!/usr/bin/env zsh
# Android 启动冒烟（P5-0 B1-B4 自动化部份）：
# 网关就绪断言 + 无崩溃 + 无报错页 + 底部导航存在。onboarding 按英文文本点过。
# 用法： smoke_android.sh [avd名，默认 han1me-16kb]
set -u
HERE="${0:A:h}"
source "$HERE/common.sh"
AVD="${1:-han1me-16kb}"
R="$OUT/smoke-android.result"; : > "$R"
export P5_ADB="$ADB"

ensure_emu "$AVD" || { fail "$R" "模拟器未上线"; exit 1 }
print "pagesize=$("$ADB" shell getconf PAGE_SIZE 2>/dev/null | tr -d '\r')" > "$OUT/device-info.txt"
"$ADB" logcat -c
"$ADB" shell pm clear "$PKG" > /dev/null 2>&1
"$ADB" shell am start -n "$PKG/$ACTIVITY" > /dev/null 2>&1
sleep 15

# onboarding 多步（英文 locale；找不到就跳过，不硬点）。
# 顺序：Welcome(Get started) → 说明页(Next) → 使用须知(Next，有阅读倒计时) → 完成(Done)。
for i in 1 2 3 4 5 6 7 8; do
  FOUND=0
  for LABEL in "Get started" "Next" "I have read and agree" "Done — take me in"; do
    OUT_TAP=$(python3 "$HERE/tap_text.py" "$LABEL")
    if [ "$OUT_TAP" != "NOTFOUND" ] && [ "$OUT_TAP" != "NODUMP" ] && [ "$OUT_TAP" != "PARSEFAIL" ]; then
      FOUND=1
      sleep 8
      break
    fi
  done
  [ "$FOUND" = "0" ] && break
done

"$ADB" shell uiautomator dump /sdcard/p5smoke.xml > /dev/null 2>&1
"$ADB" pull /sdcard/p5smoke.xml "$OUT/ui-smoke.xml" > /dev/null 2>&1
"$ADB" logcat -d | grep -iE "EchGate|NoRoute|Egress|AndroidRuntime|FATAL" > "$OUT/logcat-smoke.txt" 2>&1

# 断言 1：网关就绪（进程内起服；开关默认开）
grep -q "ECH 网关已就绪" "$OUT/logcat-smoke.txt" || { fail "$R" "未见网关就绪行"; exit 1 }
# 断言 2：无崩溃文件（debug 包 run-as 可读 cache；takePending 会在下次启动清掉，
# 单轮内检查即有效）
if "$ADB" shell "run-as $PKG ls cache" 2>/dev/null | grep -q "han1me_crash_report"; then
  fail "$R" "发现崩溃报告，见 logcat-smoke.txt"; exit 1
fi
# 断言 3：页面无报错
grep -q "Load failed" "$OUT/ui-smoke.xml" && { fail "$R" "页面出现 Load failed"; exit 1 }
# 断言 4：底部导航存在（A5 同口径）
grep -q "Home Page" "$OUT/ui-smoke.xml" || { fail "$R" "底部导航缺失"; exit 1 }
pass "$R" "网关就绪/无崩溃/无报错页/导航齐全"
