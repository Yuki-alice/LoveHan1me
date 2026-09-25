// Package gate：本地 ECH 出站网关核心。
//
// 从 `main.go` 抽出，与 CLI 解耦：桌面 CLI 与未来的 gomobile 绑定
// （Android `.aar` / iOS framework，进程内起服）复用同一套策略与 DoH/ECH 逻辑。
//
// ## 单实例约束
// 策略缓存与 ECH 配置是包级状态，一进程只调一次 [Start]。
// App 本来就只需要一个网关（一个 `127.0.0.1` 端口），与现状一致。
package gate

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

// DoH 记录类型。
const (
	qtypeA     = 1
	qtypeCNAME = 5
	qtypeHTTPS = 65
)

// plan 是某个目标域名"该怎么连"的结论。
type plan struct {
	// 按握手延迟升序的候选 IP（探测期排好序）。拨号按序尝试，
	// 单个 IP 失败就换下一个——DPI 间歇性 RST 时首个候选抖动不再等于整域不可用。
	ips []string
	// SNI 与 Host 用哪个名字（CNAME 降级时会换成真名）
	dialHost string
	useECH   bool
}

// Config 是网关的全部输入（CLI flag 与 gomobile 调用共用同一形状，
// 只用 string/int64 切片与时长毫秒——保持 gomobile 可绑定）。
type Config struct {
	ListenAddr string
	CfIPs      []string
	CfHosts    []string
	DohURL     string
	EchDomain  string
	EchB64     string
	CacheDir   string
	// TimeoutMs 上游连接超时毫秒（<=0 则用默认值）。
	TimeoutMs int64
}

// Server 是运行中的网关。Close 后端口释放，复用方（桌面进程管理 / gomobile）
// 据此做开关。
type Server struct {
	httpServer *http.Server
	listener   net.Listener
}

// Addr 返回实际监听地址（`127.0.0.1:0` 由内核分配时，调用方靠它知道端口）。
func (s *Server) Addr() string {
	if s.listener == nil {
		return ""
	}
	return s.listener.Addr().String()
}

// Close 停止网关。
func (s *Server) Close() error {
	if s.httpServer == nil {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	return s.httpServer.Shutdown(ctx)
}

var (
	plansMu sync.RWMutex
	plans   = map[string]plan{}

	dohURL      string
	cfIPs       []string
	cfHosts     []string
	connTimeout time.Duration

	echMu    sync.RWMutex
	echBytes []byte

	echCachePath string
)

// ECH 公钥配置的缓存有效期。
//
// CF 会轮换密钥，缓存太久会拿到被服务端拒绝的过期配置（实测症状是
// `tls: server rejected ECH`）。一小时是"启动够快"与"不会用到过期密钥"的折中；
// 真过期了也不致命——探测阶段会发现握手失败，把该域名退回普通路径。
const echCacheTTL = time.Hour

// Start 按 [Config] 启动网关并立即返回（服务跑在后台 goroutine）。
//
// ECH 配置拿不到不致命：Cloudflare 后的站点退回普通 TLS（很可能被重置，
// 但其它域名的 plain/alias 策略不受影响）——调用方不要因此判死网关。
func Start(cfg Config) (*Server, error) {
	dohURL = strings.TrimRight(cfg.DohURL, "?&")
	if dohURL == "" {
		dohURL = "https://dns.alidns.com/resolve"
	}
	cfIPs = append([]string(nil), cfg.CfIPs...)
	cfHosts = append([]string(nil), cfg.CfHosts...)
	connTimeout = time.Duration(cfg.TimeoutMs) * time.Millisecond
	if connTimeout <= 0 {
		connTimeout = 15 * time.Second
	}
	echCachePath = ""
	if cfg.CacheDir != "" {
		_ = os.MkdirAll(cfg.CacheDir, 0o755)
		echCachePath = filepath.Join(cfg.CacheDir, "ech-config.bin")
	}

	ech, echSrc := resolveECHConfig(cfg.EchB64, dohURL, cfg.EchDomain)
	if len(ech) == 0 {
		log.Printf("echgate: 未取得 ECH 配置，Cloudflare 后的站点将退回普通 TLS（很可能被重置）")
	} else {
		log.Printf("echgate: ECH 配置就绪（来源=%s，%d 字节）", echSrc, len(ech))
	}

	proxy := &httputil.ReverseProxy{
		Director:     director,
		Transport:    &http.Transport{DialTLSContext: dialUpstream, IdleConnTimeout: 30 * time.Second},
		ErrorHandler: onUpstreamError,
	}

	listenAddr := cfg.ListenAddr
	if listenAddr == "" {
		listenAddr = "127.0.0.1:0"
	}
	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		return nil, err
	}
	// Handler 分流：CONNECT 走正向代理隧道（兜底通道），其余走反向代理（主力通道）。
	// 两条通道的分工见 handleConnect 的 KDoc——主力通道才能用 ECH，别搞反。
	srv := &Server{listener: ln}
	srv.httpServer = &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodConnect {
				handleConnect(w, r)
				return
			}
			proxy.ServeHTTP(w, r)
		}),
	}
	go func() {
		// nolint:errcheck — Serve 的返回只在 Close 时有意义，调用方看 Close 的 error。
		_ = srv.httpServer.Serve(ln)
	}()
	return srv, nil
}

// ── 请求改写 ────────────────────────────────────────────

// director 把改写过的请求还原成真正的 https 请求。
//
// 这里同时完成策略决策。CNAME 降级时 **Host 头必须跟着换成真名**——
// CDN77 校验 SNI 与 Host 一致，只换 SNI 不换 Host 会拿到 403（实测）。
// routeCtx 随请求携带导演阶段的结论。
//
// 为什么必须传下去：director 会把 `URL.Host` 换成 dialHost（可能是 CNAME 真名），
// 于是 dialUpstream 拿到的是别名而不是原域名。如果在那里再 planFor 一次，
// 等于对**别名**重新探测——实测就是这样把已经定好的可用 IP 换成了一个不可达的。
type routeCtx struct {
	origin string // 请求原本要访问的域名（用于失效缓存）
	plan   plan
}

type routeCtxKey struct{}

func director(r *http.Request) {
	host := strings.TrimSpace(r.Header.Get("X-Ech-Target"))
	if host == "" {
		host = r.Host
	}
	if host == "" {
		return
	}
	r.Header.Del("X-Ech-Target")

	// 先换成携带结论的副本，再改 URL —— WithContext 会深拷贝 URL，
	// 顺序反过来的话改动会落在旧对象上。
	*r = *r.WithContext(context.WithValue(r.Context(), routeCtxKey{}, routeCtx{
		origin: host,
		plan:   planFor(host),
	}))

	p, _ := r.Context().Value(routeCtxKey{}).(routeCtx)
	r.URL.Scheme = "https"
	// CNAME 降级时 Host 头必须跟着换成真名：CDN77 校验 SNI 与 Host 一致，
	// 只换 SNI 不换 Host 会拿到 403（实测）。
	r.URL.Host = p.plan.dialHost
	r.Host = p.plan.dialHost
}

func onUpstreamError(w http.ResponseWriter, r *http.Request, err error) {
	origin, p := r.Host, plan{}
	if rc, ok := r.Context().Value(routeCtxKey{}).(routeCtx); ok {
		origin, p = rc.origin, rc.plan
	}
	log.Printf("echgate: upstream error origin=%s dial=%s ips=%v err=%v", origin, p.dialHost, p.ips, err)
	// 连不上多半是 IP 变了或策略过期，丢掉缓存让下次重新探测。
	invalidatePlan(origin)
	w.WriteHeader(http.StatusBadGateway)
	_, _ = io.WriteString(w, "echgate: "+err.Error())
}

// handleConnect 标准正向代理隧道（CONNECT）—— **兜底通道，不是主力**。
//
// ## 与主力通道（反向代理 + X-Ech-Target）的分工必须分清
// 主力通道里 TLS 由**网关代为**握手，所以能用 ECH、也能把 SNI 换成 CNAME 真名
// （`vdownload.hembed.com` → `…rsc.cdn77.org` 就是靠这条通的）。
// CONNECT 隧道里客户端（mpv / OkHttp）在隧道内**自己做** TLS，SNI 对网关不可控、
// 仍是明文 —— 网关帮不上 ECH 的忙。main.go 顶部"为什么不走 CONNECT"说的就是这事，
// 它对浏览器成立，对 mpv 同样成立。
//
// ⚠️ 上游 Han1meViewer 的 `echproxy.handleConnect` 也是纯隧道（它注释写着
// "ECH is negotiated by the client's own TLS inside the tunnel"）。所以照搬上游
// 只会把主力通道的 ECH 能力换掉，是对 CF 站点的**降级**，不是升级。
//
// ## 那它还有什么用
// 只剩两件，但足够当兜底：
//  1. **DoH 解析**：客户端自己解析会撞上被污染的 DNS，这里给的是干净 IP；
//  2. **逐 IP 拨号**：解析出多个 IP 时逐个试，挑第一个能建连的。
//
// 于是调用方在主力通道失败后的回退顺序变成：
// 真实 URL + 用户代理 → 真实 URL + 本通道（DoH 路由）→ 裸直连。
func handleConnect(w http.ResponseWriter, r *http.Request) {
	hostport := r.Host
	if hostport == "" {
		http.Error(w, "echgate: missing CONNECT host", http.StatusBadRequest)
		return
	}
	host, port := hostport, "443"
	if h, p, err := net.SplitHostPort(hostport); err == nil {
		host, port = h, p
	}
	if host == "" {
		http.Error(w, "echgate: missing CONNECT host", http.StatusBadRequest)
		return
	}

	ips, _ := lookupAWithCNAME(host)
	if len(ips) == 0 {
		log.Printf("echgate: CONNECT %s DoH 解析无结果", host)
		http.Error(w, "echgate: resolve "+host, http.StatusBadGateway)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), connTimeout)
	defer cancel()
	var upstream net.Conn
	var lastErr error
	for _, ip := range ips {
		upstream, lastErr = (&net.Dialer{Timeout: connTimeout}).DialContext(
			ctx, "tcp", net.JoinHostPort(ip, port),
		)
		if lastErr == nil {
			break
		}
	}
	if upstream == nil {
		log.Printf("echgate: CONNECT %s 拨号失败：%v", host, lastErr)
		http.Error(w, "echgate: connect "+host+": "+lastErr.Error(), http.StatusBadGateway)
		return
	}
	defer upstream.Close()
	log.Printf("echgate: CONNECT %s 隧道已建立（裸 TCP；IP 来自 DoH %v）", host, ips)

	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "echgate: hijack unsupported", http.StatusInternalServerError)
		return
	}
	client, buf, err := hj.Hijack()
	if err != nil {
		return
	}
	defer client.Close()

	if _, err := buf.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n"); err != nil {
		return
	}
	if err := buf.Flush(); err != nil {
		return
	}

	// 双向转发。任一端关掉就发 FIN 收掉另一端，避免 goroutine 泄漏。
	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(upstream, buf)
		if tc, ok := upstream.(*net.TCPConn); ok {
			_ = tc.CloseWrite()
		}
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(client, upstream)
		if tc, ok := client.(*net.TCPConn); ok {
			_ = tc.CloseWrite()
		}
		done <- struct{}{}
	}()
	<-done
}

// dialUpstream 按已定好的策略建立到上游的连接。
//
// 候选逐个尝试，单个 IP 内部还有两级回退（与参考实现同策略）：
//  1. ECH 握手；
//  2. ECH 被拒且服务端给了 retry_configs → 用新配置对同一 IP 重握一次（并缓存）；
//  3. 仍失败 → 对同一 IP 降级普通 TLS（保护性降级：ECH 配置过期/轮换时至少保证连通）。
//     再失败 → 下一个 IP。全部失败才返回错误（调用方记 502 并失效本域缓存）。
func dialUpstream(ctx context.Context, network, addr string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		host, port = addr, "443"
	}

	// 正常情况下结论由 director 带下来；context 丢失时（Transport 内部重试）
	// 才回退到按域名现查。
	p, ok := ctx.Value(routeCtxKey{}).(routeCtx)
	if !ok {
		p = routeCtx{origin: host, plan: planFor(host)}
	}

	if len(p.plan.ips) == 0 {
		return nil, fmt.Errorf("echgate: no dial candidates for %s", p.origin)
	}

	d := &net.Dialer{Timeout: connTimeout}
	var lastErr error
	for _, ip := range p.plan.ips {
		conn, err := dialOne(ctx, d, ip, port, p.plan.dialHost, p.plan.useECH)
		if err == nil {
			return conn, nil
		}
		lastErr = err
	}
	return nil, lastErr
}

// dialOne 对单个 IP 建连（ECH → retry_configs 重试 → 普通 TLS 降级）。
func dialOne(
	ctx context.Context,
	d *net.Dialer,
	ip, port, sni string,
	useECH bool,
) (net.Conn, error) {
	// 对给定 TCP 连接做一次 TLS 握手。调用方负责失败时 Close。
	handshake := func(raw net.Conn, cfg *tls.Config) (*tls.Conn, error) {
		conn := tls.Client(raw, cfg)
		hctx, cancel := context.WithTimeout(ctx, 5*time.Second)
		defer cancel()
		if err := conn.HandshakeContext(hctx); err != nil {
			return nil, err
		}
		return conn, nil
	}

	base := &tls.Config{
		ServerName: sni,
		MinVersion: tls.VersionTLS12,
		// 注意不要加 NextProtos h2：上游 ReverseProxy 的 Transport 只会 HTTP/1.1，
		// 协商出 h2 会导致 "malformed HTTP response"（实测踩过）。
		// 参考实现加 h2 是因为它的出站 client 配了 ForceAttemptHTTP2 + h2 传输。
	}
	dialTCP := func() (net.Conn, error) {
		return d.DialContext(ctx, "tcp", net.JoinHostPort(ip, port))
	}

	if !useECH {
		raw, err := dialTCP()
		if err != nil {
			return nil, err
		}
		conn, err := handshake(raw, base)
		if err != nil {
			raw.Close()
			return nil, err
		}
		return conn, nil
	}

	echCfg := base.Clone()
	echCfg.MinVersion = tls.VersionTLS13
	echCfg.EncryptedClientHelloConfigList = currentECH()

	raw, err := dialTCP()
	if err != nil {
		return nil, err
	}
	if conn, err := handshake(raw, echCfg); err == nil {
		if conn.ConnectionState().ECHAccepted {
			return conn, nil
		}
		// 配置被服务器忽略（未接受）→ 降级普通 TLS。
		log.Printf("echgate: %s ECH 未被接受，降级普通 TLS", sni)
		conn.Close()
		raw.Close()
	} else {
		var rej *tls.ECHRejectionError
		if errors.As(err, &rej) && len(rej.RetryConfigList) > 0 {
			// 服务端给了新配置：缓存并对同一 IP 重握一次。
			setECH(rej.RetryConfigList)
			writeECHCache(rej.RetryConfigList)
			raw.Close()
			if raw2, err2 := dialTCP(); err2 == nil {
				retryCfg := base.Clone()
				retryCfg.MinVersion = tls.VersionTLS13
				retryCfg.EncryptedClientHelloConfigList = rej.RetryConfigList
				if conn2, err2 := handshake(raw2, retryCfg); err2 == nil &&
					conn2.ConnectionState().ECHAccepted {
					log.Printf("echgate: %s ECH retry_configs 兜底成功", sni)
					return conn2, nil
				} else {
					if err2 == nil {
						conn2.Close()
					}
					raw2.Close()
				}
			}
			log.Printf("echgate: %s ECH retry_configs 兜底失败，降级普通 TLS", sni)
		} else {
			raw.Close()
		}
	}

	// 保护性降级：普通 TLS（SNI 明文）。ECH 配置过期时至少保证连通；
	// SNI 被针对的域名本来也无其它活路，失败会继续试下一个 IP。
	raw3, err := dialTCP()
	if err != nil {
		return nil, err
	}
	conn, err := handshake(raw3, base)
	if err != nil {
		raw3.Close()
		return nil, err
	}
	return conn, nil
}

// ── 策略决策 ────────────────────────────────────────────

// planFor 取域名策略；未命中则**同步探测**一次并缓存。
//
// 探测有代价（最坏要试到第三档），但每个域名只发生一次：首次请求慢一点，
// 之后全走缓存，行为确定——比"失败一次再重试"好排查。
func planFor(host string) plan {
	plansMu.RLock()
	if p, ok := plans[host]; ok {
		plansMu.RUnlock()
		return p
	}
	plansMu.RUnlock()

	p := probePlan(host)

	plansMu.Lock()
	plans[host] = p
	plansMu.Unlock()
	log.Printf("echgate: plan %s -> ips=%v dial=%s ech=%v", host, p.ips, p.dialHost, p.useECH)
	return p
}

func invalidatePlan(host string) {
	plansMu.Lock()
	delete(plans, host)
	plansMu.Unlock()
}

func probePlan(host string) plan {
	// A 查询一次供后面三档共用（应答里本来就带着整条 CNAME 链，见 lookupAWithCNAME）。
	// 注意 IP 可能是被污染的假地址——必须用 tryHandshake 验证，不能盲信。
	hostIPs, alias := lookupAWithCNAME(host)

	// ① Cloudflare 后的**站点域名**：候选 CF IP + ECH。
	//
	//    只对调用方点名的站点（cf-hosts）做这一步，不能对任意域名都试：
	//    ECH 握手到 CF 边缘时，外层 SNI 是 cloudflare-ech.com，CF 会照常接受握手，
	//    于是"非 CF 站点"也会被误判为可用——实测视频 CDN 就这样被错判，
	//    结果走了一条慢得多的路径（40 秒都没下完）。
	//
	//    ⚠️ 探测必须用 ECH 握手：这类域名的普通 TLS 会被重置，
	//    拿普通握手去试会把 CF 站点误判成"不是边缘"。
	//
	//    候选 = 该域名自己 DoH 解析出的 IP + 调用方给的 IP 池（去重）：
	//    IP 封锁常只封一批边缘 IP（如 hanime 观测到的那几个），目标域名的
	//    真实边缘 IP 可能恰好可达——只用一家的池等于把姊妹站也判死
	//    （javchu.com 实测：hanime 的池不通、自己 DoH 出来的 104.21.7.70 可达）。
	if isCFHost(host) && len(currentECH()) > 0 {
		// 本域 DoH IP 放前面：探测只取前 4 个，它是该分区最可能的边缘；
		// 调用方池子放后面当补充。最终按握手延迟排序，顺序只影响探测轮次。
		candidates := append(append([]string(nil), hostIPs...), cfIPs...)
		if sorted := probeCandidates(dedupe(candidates), host, true); len(sorted) > 0 {
			return plan{ips: sorted, dialHost: host, useECH: true}
		}
		log.Printf("echgate: %s 声明为 CF 站点但 ECH 试不通，转普通路径", host)
	}

	// ② CNAME 真名降级（hostIPs/alias 即上面的查询结果，不再查第二遍）。
	//    放在普通 TLS 之前：有 CNAME 基本说明站点托管在 CDN 上，
	//    而原域名大概率被阻断，真名往往能通——先试它能省掉一轮注定失败的等待。
	if alias != "" && !strings.EqualFold(alias, host) {
		aliasIPs, _ := lookupAWithCNAME(alias)
		if sorted := probeCandidates(aliasIPs, alias, false); len(sorted) > 0 {
			log.Printf("echgate: %s 被阻断，改用 CNAME 真名 %s @ %v", host, alias, sorted)
			return plan{ips: sorted, dialHost: alias}
		}
	}

	// ③ 普通 TLS：用该域名自己的解析结果。
	if sorted := probeCandidates(hostIPs, host, false); len(sorted) > 0 {
		return plan{ips: sorted, dialHost: host}
	}

	// 都试不通：仍旧用原域名 + 解析结果（拨号期逐个尝试，至少把真实错误暴露出来，
	// 而不是在计划阶段就判死）。
	return plan{ips: hostIPs, dialHost: host}
}

// probeCandidates **并发**探测候选 IP，返回握手成功的那些（按延迟升序）。
//
// 为什么不是"第一个成功的就用"：CDN 的边缘 IP 分布在不同地区，
// 先撞上的那个可能在美西（实测 CDN77 返回 `X-77-POP: sanjoseUSC`），
// 直接用会明显拖慢视频起播与拖动。并发探测的总耗时约等于最慢的一个，
// 但换来的是后续每一次请求都优先走最近的那个边缘——拨号期还会按序 failover。
func probeCandidates(ips []string, sni string, useECH bool) []string {
	candidates := head(ips, 6)
	if len(candidates) == 0 {
		return nil
	}

	type result struct {
		ip string
		ms int64
	}
	ch := make(chan result, len(candidates))
	for _, ip := range candidates {
		go func(ip string) {
			ms, err := handshakeLatency(ip, sni, useECH)
			if err != nil {
				ch <- result{ip: ip, ms: -1}
				return
			}
			ch <- result{ip: ip, ms: ms}
		}(ip)
	}

	var ok []result
	for range candidates {
		if r := <-ch; r.ms >= 0 {
			ok = append(ok, r)
		}
	}
	sort.Slice(ok, func(i, j int) bool { return ok[i].ms < ok[j].ms })
	out := make([]string, 0, len(ok))
	for _, r := range ok {
		out = append(out, r.ip)
	}
	if len(out) > 0 {
		log.Printf("echgate: %s 候选 %v（%d/%d 可用）", sni, out, len(out), len(candidates))
	}
	return out
}

// handshakeLatency 做一次 TLS 握手并返回耗时（失败返回 error）。
func handshakeLatency(ip, sni string, useECH bool) (int64, error) {
	cfg := &tls.Config{ServerName: sni, MinVersion: tls.VersionTLS12}
	if useECH {
		cfg.MinVersion = tls.VersionTLS13
		cfg.EncryptedClientHelloConfigList = currentECH()
	}
	d := &net.Dialer{Timeout: 4 * time.Second}
	start := time.Now()
	raw, err := d.Dial("tcp", net.JoinHostPort(ip, "443"))
	if err != nil {
		return 0, err
	}
	defer raw.Close()
	conn := tls.Client(raw, cfg)
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	if err := conn.Handshake(); err != nil {
		return 0, err
	}
	return time.Since(start).Milliseconds(), nil
}

// isCFHost 判断某域名是否属于调用方声明的 Cloudflare 站点（含子域）。
func isCFHost(host string) bool {
	h := strings.ToLower(host)
	for _, c := range cfHosts {
		c = strings.ToLower(c)
		if h == c || strings.HasSuffix(h, "."+c) {
			return true
		}
	}
	return false
}

// head 取前 n 个，避免在注定超时的地址上耗掉整段探测时间。
func head(s []string, n int) []string {
	if len(s) > n {
		return s[:n]
	}
	return s
}

// dedupe 去重并去空，保持首次出现顺序（探测顺序即优先级）。
func dedupe(s []string) []string {
	seen := make(map[string]struct{}, len(s))
	out := make([]string, 0, len(s))
	for _, p := range s {
		if p == "" {
			continue
		}
		if _, ok := seen[p]; ok {
			continue
		}
		seen[p] = struct{}{}
		out = append(out, p)
	}
	return out
}

// ── ECH 配置 ────────────────────────────────────────────

func currentECH() []byte {
	echMu.RLock()
	defer echMu.RUnlock()
	return echBytes
}

func setECH(b []byte) {
	echMu.Lock()
	echBytes = b
	echMu.Unlock()
}

func resolveECHConfig(flagB64, doh, domain string) ([]byte, string) {
	if flagB64 != "" {
		if b, err := base64.StdEncoding.DecodeString(strings.TrimSpace(flagB64)); err == nil {
			setECH(b)
			return b, "flag"
		}
	}

	// 先吃缓存：网关越早就绪，主页那批图片越可能赶上网关（实测图片本身没问题，
	// 之前失败是因为首页在网关就绪前就开始加载了）。
	if b, ok := readECHCache(); ok {
		setECH(b)
		return b, "cache"
	}

	// 只查 DNS、不访问该域名本身——cloudflare-ech.com 在国内不可达，但查它的记录不受影响。
	for _, rec := range lookupHTTPS(doh, domain) {
		if b64 := extractECHParam(rec); b64 != "" {
			if b, err := base64.StdEncoding.DecodeString(b64); err == nil {
				setECH(b)
				writeECHCache(b)
				return b, "doh:" + domain
			}
		}
	}

	b, err := base64.StdEncoding.DecodeString(fallbackECHB64)
	if err != nil {
		return nil, "none"
	}
	setECH(b)
	return b, "builtin-fallback"
}

func readECHCache() ([]byte, bool) {
	if echCachePath == "" {
		return nil, false
	}
	info, err := os.Stat(echCachePath)
	if err != nil || time.Since(info.ModTime()) > echCacheTTL {
		return nil, false
	}
	b, err := os.ReadFile(echCachePath)
	if err != nil || len(b) == 0 {
		return nil, false
	}
	return b, true
}

func writeECHCache(b []byte) {
	if echCachePath == "" {
		return
	}
	_ = os.WriteFile(echCachePath, b, 0o644)
}

// 内置兜底：cloudflare-ech.com 的 HTTPS 记录实测值。公钥会轮换，
// 仅供 DoH 也挂掉时顶一下——实测硬编码那份过期后会被服务端拒绝（`server rejected ECH`）。
const fallbackECHB64 = "AEX+DQBBGQAgACDXhbyp6YejOIxAAP8ReUkTLB47ufVDU2R8FaHdQmzcPwAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA="

// extractECHParam 从 HTTPS 记录的文本形式里取 ech="…" 的值，
// 形如：`1 . alpn="h3,h2" ipv4hint="…" ech="AEX+…" ipv6hint="…"`
func extractECHParam(rec string) string {
	const key = `ech="`
	i := strings.Index(rec, key)
	if i < 0 {
		return ""
	}
	rest := rec[i+len(key):]
	j := strings.IndexByte(rest, '"')
	if j < 0 {
		return ""
	}
	return rest[:j]
}

// ── DoH（JSON 格式） ────────────────────────────────────

type dohAnswer struct {
	Type int    `json:"type"`
	Data string `json:"data"`
}

type dohResponse struct {
	Status int         `json:"Status"`
	Answer []dohAnswer `json:"Answer"`
}

func dohQuery(name string, qtype int) ([]dohAnswer, error) {
	sep := "?"
	if strings.Contains(dohURL, "?") {
		sep = "&"
	}
	u := fmt.Sprintf("%s%sname=%s&type=%d", dohURL, sep, name, qtype)
	req, err := http.NewRequest(http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	// JSON 形式：CF / Google / 阿里云的 JSON 端点都认这个 accept。
	req.Header.Set("accept", "application/dns-json")

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	resp, err := http.DefaultClient.Do(req.WithContext(ctx))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("doh http %d", resp.StatusCode)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	var out dohResponse
	if err := json.Unmarshal(body, &out); err != nil {
		return nil, err
	}
	return out.Answer, nil
}

// lookupAWithCNAME 一次 A 查询同时取回 IP 与 CNAME 目标。
//
// 为什么不单独查 CNAME：阿里云等解析端对 `type=CNAME` 只在"该名字本身就是
// CNAME 且未被展开"时才回，实测会漏；而 A 查询的应答里本来就带着整条 CNAME 链。
//
// 注意 IP 可能是被污染的假地址——调用方必须用 tryHandshake 验证，不能盲信。
func lookupAWithCNAME(host string) ([]string, string) {
	ans, err := dohQuery(host, qtypeA)
	if err != nil {
		return nil, ""
	}
	var ips []string
	cname := ""
	for _, a := range ans {
		switch {
		case a.Type == qtypeCNAME && cname == "":
			cname = strings.TrimSuffix(a.Data, ".")
		case a.Type == qtypeA && net.ParseIP(a.Data) != nil:
			ips = append(ips, a.Data)
		}
	}
	return ips, cname
}

func lookupHTTPS(doh, host string) []string {
	saved := dohURL
	dohURL = strings.TrimRight(doh, "?&")
	defer func() { dohURL = saved }()

	ans, err := dohQuery(host, qtypeHTTPS)
	if err != nil {
		return nil
	}
	var out []string
	for _, a := range ans {
		if a.Type == qtypeHTTPS {
			out = append(out, a.Data)
		}
	}
	return out
}

// SplitList 把逗号分隔列表切成干净的条目（CLI flag 与 gomobile 共用）。
func SplitList(s string) []string {
	out := make([]string, 0, 4)
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
