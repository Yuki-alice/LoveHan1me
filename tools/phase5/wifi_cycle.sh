#!/usr/bin/env zsh
# 断网熔断循环（P5-1 T3 自动化）：断网造传输失败攒熔断 → 开网复位 → 网关恢复。
# 原理：NetworkCallback 只认 onAvailable，disable 本身不复位（顺序不能反）；
# 复位后下一次成功即走网关，无需等 5 分钟冷却。
# 关键教训（2026-10-04 调试得出）：熔断要"持续失败"来攒，流量burst后被动等待
# 永远等不到——断网窗口内必须一边造流量一边查；断言只看阶段起点之后的新行。
# 用法： wifi_cycle.sh [avd名，默认 han1me-16kb]
set -u
HERE="${0:A:h}"
source "$HERE/common.sh"
AVD="${1:-han1me-16kb}"
R="$OUT/wifi-cycle.result"; : > "$R"

ensure_emu "$AVD" || { fail "$R" "模拟器未上线"; exit 1 }
export P5_ADB="$ADB"
"$ADB" logcat -c
# 冷启动（不清数据，只杀进程：onboarding 已过，网关会重新拉起并打就绪行）
"$ADB" shell am force-stop "$PKG" > /dev/null 2>&1
sleep 3
"$ADB" shell am start -n "$PKG/$ACTIVITY" > /dev/null 2>&1
LOG="$OUT/logcat-wifi.txt"
"$ADB" logcat > "$LOG" 2>&1 &
LOGCAT_PID=$!
# 无论成败都把网络开回来：断网是测试手段，不能留在设备上坑下一轮。
# airplane 开关比重建单个通道可靠（见恢复步骤注释）。
trap '$ADB shell settings put global airplane_mode_on 0 > /dev/null 2>&1; $ADB shell svc wifi enable > /dev/null 2>&1; $ADB shell svc data enable > /dev/null 2>&1; kill $LOGCAT_PID 2>/dev/null' EXIT

# 自 MARK 行起查模式（wait_log 看整文件，会命中历史行；macOS tail -n +N
# 不可靠，用 awk 按行号切）
mark() { wc -l < "$LOG" | tr -d ' '; }
wait_since() {
  local mark="$1" pattern="$2" timeout="$3" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    awk -v m="$mark" 'NR>=m' "$LOG" 2>/dev/null | grep -qE "$pattern" && return 0
    sleep 3; waited=$((waited + 3))
  done
  return 1
}

# 基线：网关就绪
M=$(mark)
wait_since "$M" "ECH 网关已就绪" 60 || { fail "$R" "网关未就绪"; exit 1 }

# 更新弹窗会吃掉手势：先关掉，否则流量起不来
for i in 1 2; do
  python3 "$HERE/tap_text.py" "Ignore this version" | grep -q TAP || break
  sleep 3
done

# 切页必有流量：发现页自动浏览 + 首页推荐（swipe 在弹窗/吸顶时不可靠，只做补充）
page_cycle() {
  python3 "$HERE/tap_text.py" "Discover" > /dev/null 2>&1; sleep 10
  python3 "$HERE/tap_text.py" "Home Page" > /dev/null 2>&1; sleep 10
  "$ADB" shell input swipe 540 500 540 1500 400 > /dev/null 2>&1; sleep 8
}

# 基线 200（自当前起）
M=$(mark); page_cycle
wait_since "$M" "127.0.0.1:[0-9]+ \(200\)" 90 || { fail "$R" "基线无网关 200 行"; exit 1 }

# 断网（双通道都掐：模拟器 wifi 关了还剩 cellular），一边造流量一边等熔断
"$ADB" shell svc wifi disable > /dev/null 2>&1
"$ADB" shell svc data disable > /dev/null 2>&1
M=$(mark)
MELTED=0
for i in 1 2 3 4 5 6 7 8; do
  page_cycle
  if awk -v m="$M" 'NR>=m' "$LOG" 2>/dev/null | grep -q "网关熔断"; then MELTED=1; break; fi
done
[ "$MELTED" = "1" ] || { fail "$R" "断网后未见熔断（8 轮切页）"; exit 1 }

# 开网 → 复位 → 网关恢复（不断言"恢复"行：reset 是静默的，看网关 200 回来）。
# 用 airplane 开关恢复（svc enable 经常只开开关不重建会话，airplane 最可靠；
# 同走 onAvailable → 同一复位路径，测的仍是同一条代码）。
"$ADB" shell settings put global airplane_mode_on 1 > /dev/null 2>&1
"$ADB" shell "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true" > /dev/null 2>&1
sleep 5
"$ADB" shell settings put global airplane_mode_on 0 > /dev/null 2>&1
"$ADB" shell "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false" > /dev/null 2>&1
sleep 20
M=$(mark); page_cycle
wait_since "$M" "127.0.0.1:[0-9]+ \(200\)" 90 || { fail "$R" "开网后网关未恢复 200"; exit 1 }

# 无崩溃
if "$ADB" shell "run-as $PKG ls cache" 2>/dev/null | grep -q "han1me_crash_report"; then
  fail "$R" "发现崩溃报告"; exit 1
fi
pass "$R" "熔断出现/开网复位/网关恢复200/无崩溃"
