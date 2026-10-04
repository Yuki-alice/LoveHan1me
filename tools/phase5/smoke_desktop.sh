#!/usr/bin/env zsh
# 桌面启动冒烟：网关就绪 + 无崩溃（交互式页面不断言，需人工看一眼）。
# 用法： smoke_desktop.sh [观察秒数，默认 150]
# 注意：会真实打开应用窗口；结束时只杀应用进程，不碰 Gradle daemon。
set -u
HERE="${0:A:h}"
source "$HERE/common.sh"
DWELL="${1:-150}"
R="$OUT/smoke-desktop.result"; : > "$R"
cd "$HERE/../.." || exit 1

LOG="$OUT/desktop-run.log"
(./gradlew :desktopApp:run --console=plain > "$LOG" 2>&1 & echo $! > "$OUT/desktop-run.pid")
sleep 10
# 文案两端不同（Android"已就绪"/桌面"就绪"），模式兼容两者
wait_log "$LOG" "ECH 网关(已)?就绪" 120 || { fail "$R" "网关未就绪"; exit 1 }
sleep "$DWELL"
# 进程还活着？
APP_PID=$(pgrep -f "lovehan1me.desktop.Main" | head -n 1)
[ -n "$APP_PID" ] || { fail "$R" "应用进程已退出（崩溃？查 desktop-run.log 尾部）"; exit 1 }
# 崩溃文件（JVM tmpdir = 本 shell 的 $TMPDIR；gradle run 继承它）
ls "${TMPDIR:-/tmp}/han1me_crash_report.txt" 2>/dev/null && {
  fail "$R" "发现崩溃报告 ${TMPDIR:-/tmp}/han1me_crash_report.txt"; exit 1
}
# 收尾：只杀应用，不杀 daemon
pkill -f "lovehan1me.desktop.Main" 2>/dev/null
pass "$R" "网关就绪/进程存活/无崩溃文件（页面请人工确认）"
