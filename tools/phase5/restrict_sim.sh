#!/usr/bin/env bash
# 受限网模拟器：在本机造出**可判定的受限形态**，让 R2/R4/R5 不必等"换网"。
#
# 背景：docs/plan/网络连接调度器.md 的 7.0 写明本机网络不受限，R1–R6 因此一直挂起。
# 但受限形态里至少有三件事本机可造，且比真受限网更可控（可重复、可断言）：
#
#   1. 直连不可用      → hosts 投毒：站点域名解析到 127.0.0.1（连接被拒，等价"直连撞墙"）
#   2. 网关出口不可用  → hosts 投毒 DoH 端点：网关拿不到干净 IP，上游必失败 → 502
#   3. 代理可用        → 本地起一个 CONNECT 转发代理（fake_proxy.py），让位路径有真出口
#
# 造不出来的（仍需真受限网）：真 SNI 阻断（R1）、真 CF 挑战（R3）、三端真机冒烟（R6）。
#
# 用法：
#   ./restrict_sim.sh hosts-blackhole   # 写入投毒（需 sudo）
#   ./restrict_sim.sh hosts-restore     # 还原（需 sudo）
#   ./restrict_sim.sh proxy-up [port]   # 起本地转发代理（默认 8899）
#   ./restrict_sim.sh proxy-down
#   ./restrict_sim.sh status
#
# 每个子命令写 $OUT/restrict_sim.<mode>.result（KEY=VALUE），与既有脚本同约定。
#
# ⚠️ 未在 Windows 上执行过（本仓既有 phase5 脚本也是 macOS/zsh 口径）。
#    首次在受限形态下使用前，先在目标机跑一遍 `status` 确认行为符合预期。

set -u

OUT="${PHASE5_OUT:-/tmp/phase5}"
mkdir -p "$OUT"
HOSTS="/etc/hosts"
BACKUP="$OUT/hosts.backup"
MARKER="# lovehan1me-restrict-sim"
PROXY_PORT="${1:-8899}"
PROXY_PID="$OUT/fake_proxy.pid"
PROXY_LOG="$OUT/fake_proxy.log"

# 投毒目标：站点域名（直连不可用）+ DoH 端点（网关拿不到干净 IP）。
# 127.0.0.1 上没有 HTTPS 服务 ⇒ 连接被拒，比"超时"快，断言不会等到天荒地老。
POISON_HOSTS="hanime1.me www.hanime1.me hanime1.com www.hanime1.com dns.alidns.com"

result() {
  local name="$1" status="$2" detail="$3"
  printf 'STATUS=%s\nDETAIL=%s\n' "$status" "$detail" > "$OUT/restrict_sim.$name.result"
  printf '[%s] %s: %s\n' "$status" "$name" "$detail"
}

hosts_backup() {
  [ -f "$BACKUP" ] || cp "$HOSTS" "$BACKUP"
}

hosts_blackhole() {
  hosts_backup
  # 幂等：先清掉上一轮的投毒行，再追加。
  grep -v "$MARKER" "$HOSTS" > "$OUT/hosts.tmp" 2>/dev/null || true
  {
    cat "$OUT/hosts.tmp"
    echo "$MARKER begin"
    for h in $POISON_HOSTS; do echo "127.0.0.1 $h $MARKER"; done
    echo "$MARKER end"
  } > "$OUT/hosts.new"
  if cp "$OUT/hosts.new" "$HOSTS"; then
    result hosts-blackhole PASS "已投毒：$POISON_HOSTS -> 127.0.0.1（备份在 $BACKUP）"
  else
    result hosts-blackhole FAIL "写 $HOSTS 失败（需要 sudo）"
  fi
}

hosts_restore() {
  if [ -f "$BACKUP" ]; then
    if cp "$BACKUP" "$HOSTS"; then
      result hosts-restore PASS "已从 $BACKUP 还原"
    else
      result hosts-restore FAIL "还原失败（需要 sudo）"
    fi
  else
    result hosts-restore FAIL "没有备份 $BACKUP，未改动 $HOSTS"
  fi
}

proxy_up() {
  local port="$PROXY_PORT"
  if [ -f "$PROXY_PID" ] && kill -0 "$(cat "$PROXY_PID")" 2>/dev/null; then
    result proxy-up PASS "代理已在运行 pid=$(cat "$PROXY_PID") port=$port"
    return
  fi
  local here
  here="$(cd "$(dirname "$0")" && pwd)"
  nohup python3 "$here/fake_proxy.py" "$port" > "$PROXY_LOG" 2>&1 &
  echo $! > "$PROXY_PID"
  sleep 1
  if kill -0 "$(cat "$PROXY_PID")" 2>/dev/null; then
    result proxy-up PASS "转发代理已起 127.0.0.1:$port（日志 $PROXY_LOG）"
  else
    result proxy-up FAIL "代理未能启动，见 $PROXY_LOG"
  fi
}

proxy_down() {
  if [ -f "$PROXY_PID" ]; then
    kill "$(cat "$PROXY_PID")" 2>/dev/null || true
    rm -f "$PROXY_PID"
    result proxy-down PASS "代理已停"
  else
    result proxy-down PASS "本来就没在跑"
  fi
}

status() {
  echo "── hosts 投毒 ──"
  grep "$MARKER" "$HOSTS" 2>/dev/null || echo "  （无投毒行）"
  echo "── 本地代理 ──"
  if [ -f "$PROXY_PID" ] && kill -0 "$(cat "$PROXY_PID")" 2>/dev/null; then
    echo "  运行中 pid=$(cat "$PROXY_PID")"
  else
    echo "  未运行"
  fi
  echo "── 解析验证（投毒生效时应指向 127.0.0.1）──"
  for h in hanime1.me dns.alidns.com; do
    printf '  %s -> %s\n' "$h" "$(getent hosts "$h" 2>/dev/null | head -1 | awk '{print $1}' || echo '?')"
  done
}

case "${1:-}" in
  hosts-blackhole) hosts_blackhole ;;
  hosts-restore)   hosts_restore ;;
  proxy-up)        shift; PROXY_PORT="${1:-8899}"; proxy_up ;;
  proxy-down)      proxy_down ;;
  status)          status ;;
  *)
    echo "用法: $0 {hosts-blackhole|hosts-restore|proxy-up [port]|proxy-down|status}"
    exit 2
    ;;
esac
