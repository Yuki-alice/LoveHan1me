// Command echgate：本地出站网关。
//
// ## 它解决什么
// 直连被 SNI 阻断的站点时，TLS 的 ClientHello 里 SNI 是明文，DPI 看到就 RST。
// 实测（2026-09-22）：TCP 能握手（0.2–1.0s），TLS 阶段必被重置；加 `-k` 忽略
// 证书校验也一样 ⇒ 不是证书问题。
//
// 网关按目标域名**分三种策略**出站（策略按域名缓存，首次探测后即固定）：
//
//	ECH     目标在 Cloudflare 后 → ECH 加密 ClientHello（SNI 藏进加密信封）
//	plain   没被阻断的域名 → 普通 TLS
//	alias   被阻断且不在 CF 后 → **用它的 CNAME 真名做 SNI+Host**
//
// 第三条是实测挖出来的：视频直链 `vdownload.hembed.com` 走 CDN77，
// 它自己（CNAME 目标 `…rsc.cdn77.org`）不在黑名单上，而原域名在。
// 换成真名后 `206 Partial Content / video/mp4` 连续 3/3 成功。
//
// ## 它不解决什么
// 只管"把请求送出去"。IP 与 ECH 公钥配置都由它自己经 DoH 取，
// 调用方只负责把请求改写进来。
//
// ## 请求约定
//
//	GET http://127.0.0.1:<port>/path  +  X-Ech-Target: hanime1.me
//
// 网关还原为 `https://hanime1.me/path` 并按策略出站。
//
// 为什么不走 CONNECT 隧道：浏览器发 CONNECT 后自己做 TLS，SNI 仍是明文，
// 网关帮不上忙。这条通道只给 App 自己的 HTTP 客户端与 mpv 用。
package main

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"os"
	"path/filepath"
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
	ip       string // 连哪个 IP
	dialHost string // SNI 与 Host 用哪个名字（CNAME 降级时会换成真名）
	useECH   bool
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

func main() {
	listen := flag.String("listen", "127.0.0.1:0", "监听地址；端口为 0 时由内核分配并把实际端口打到 stdout")
	cfIPsFlag := flag.String("ip-list", "", "Cloudflare 候选 IP（逗号分隔）；只对 --cf-hosts 里的域名使用")
	cfHostsFlag := flag.String("cf-hosts", "", "属于 Cloudflare 的站点域名（逗号分隔）；只有它们会走 CF IP + ECH")
	doh := flag.String("doh", "https://dns.alidns.com/resolve", "DoH 端点（JSON 格式）")
	echDomain := flag.String("ech-domain", "cloudflare-ech.com", "ECH 公钥配置来源域（CF 官方 fallback）")
	echB64Flag := flag.String("ech-b64", "", "ECH 公钥配置 base64；为空则经 DoH 取")
	cacheDir := flag.String("cache-dir", "", "缓存目录；用来缓存 ECH 公钥配置，让网关**秒级就绪**（不填则不缓存）")
	timeout := flag.Duration("timeout", 15*time.Second, "上游连接超时")
	flag.Parse()

	dohURL = strings.TrimRight(*doh, "?&")
	cfIPs = splitList(*cfIPsFlag)
	cfHosts = splitList(*cfHostsFlag)
	connTimeout = *timeout
	echCachePath = ""
	if *cacheDir != "" {
		_ = os.MkdirAll(*cacheDir, 0o755)
		echCachePath = filepath.Join(*cacheDir, "ech-config.bin")
	}

	ech, echSrc := resolveECHConfig(*echB64Flag, dohURL, *echDomain)
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

	ln, err := net.Listen("tcp", *listen)
	if err != nil {
		log.Fatalf("echgate: listen failed: %v", err)
	}
	fmt.Printf("LISTENING %s\n", ln.Addr().String())
	log.Printf("echgate: listening on %s (cf-ips=%v cf-hosts=%v)", ln.Addr().String(), cfIPs, cfHosts)
	log.Fatal((&http.Server{Handler: proxy}).Serve(ln))
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
	log.Printf("echgate: upstream error origin=%s dial=%s ip=%s err=%v", origin, p.dialHost, p.ip, err)
	// 连不上多半是 IP 变了或策略过期，丢掉缓存让下次重新探测。
	invalidatePlan(origin)
	w.WriteHeader(http.StatusBadGateway)
	_, _ = io.WriteString(w, "echgate: "+err.Error())
}

// dialUpstream 按已定好的策略建立到上游的连接。
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

	cfg := &tls.Config{MinVersion: tls.VersionTLS12, ServerName: p.plan.dialHost}
	if p.plan.useECH {
		cfg.MinVersion = tls.VersionTLS13
		cfg.EncryptedClientHelloConfigList = currentECH()
	}

	d := &net.Dialer{Timeout: connTimeout}
	raw, err := d.DialContext(ctx, "tcp", net.JoinHostPort(p.plan.ip, port))
	if err != nil {
		return nil, err
	}
	conn := tls.Client(raw, cfg)
	if err := conn.HandshakeContext(ctx); err != nil {
		raw.Close()
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
	log.Printf("echgate: plan %s -> ip=%s dial=%s ech=%v", host, p.ip, p.dialHost, p.useECH)
	return p
}

func invalidatePlan(host string) {
	plansMu.Lock()
	delete(plans, host)
	plansMu.Unlock()
}

func probePlan(host string) plan {
	// ① Cloudflare 后的**站点域名**：候选 CF IP + ECH。
	//
	//    只对调用方点名的站点（--cf-hosts）做这一步，不能对任意域名都试：
	//    ECH 握手到 CF 边缘时，外层 SNI 是 cloudflare-ech.com，CF 会照常接受握手，
	//    于是"非 CF 站点"也会被误判为可用——实测视频 CDN 就这样被错判，
	//    结果走了一条慢得多的路径（40 秒都没下完）。
	//
	//    ⚠️ 探测必须用 ECH 握手：这类域名的普通 TLS 会被重置，
	//    拿普通握手去试会把 CF 站点误判成"不是边缘"。
	if isCFHost(host) && len(cfIPs) > 0 && len(currentECH()) > 0 {
		if ip, ok := pickFastest(cfIPs, host, true); ok {
			return plan{ip: ip, dialHost: host, useECH: true}
		}
		log.Printf("echgate: %s 声明为 CF 站点但 ECH 试不通，转普通路径", host)
	}

	// ② 后面两档都要解析结果，一次 A 查询顺便拿到 CNAME
	//    （解析器会把整条链一并返回，比单独 `type=CNAME` 可靠，也少一半 DoH 请求）。
	hostIPs, alias := lookupAWithCNAME(host)

	// ③ CNAME 真名降级。放在普通 TLS 之前：有 CNAME 基本说明站点托管在 CDN 上，
	//    而原域名大概率被阻断，真名往往能通——先试它能省掉一轮注定失败的等待。
	if alias != "" && !strings.EqualFold(alias, host) {
		aliasIPs, _ := lookupAWithCNAME(alias)
		if ip, ok := pickFastest(aliasIPs, alias, false); ok {
			log.Printf("echgate: %s 被阻断，改用 CNAME 真名 %s @ %s", host, alias, ip)
			return plan{ip: ip, dialHost: alias}
		}
	}

	// ④ 普通 TLS：用该域名自己的解析结果。
	if ip, ok := pickFastest(hostIPs, host, false); ok {
		return plan{ip: ip, dialHost: host}
	}

	// 都试不通：仍旧用原域名 + 第一个解析结果，让上游错误如实暴露。
	ip := ""
	if len(hostIPs) > 0 {
		ip = hostIPs[0]
	}
	return plan{ip: ip, dialHost: host}
}

// pickFastest **并发**探测候选 IP，返回握手最快的那一个。
//
// 为什么不是"第一个成功的就用"：CDN 的边缘 IP 分布在不同地区，
// 先撞上的那个可能在美西（实测 CDN77 返回 `X-77-POP: sanjoseUSC`），
// 直接用会明显拖慢视频起播与拖动。并发探测的总耗时约等于最慢的一个，
// 但换来的是后续每一次请求都走最近的那个边缘。
func pickFastest(ips []string, sni string, useECH bool) (string, bool) {
	candidates := head(ips, 4)
	if len(candidates) == 0 {
		return "", false
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

	best, bestMs := "", int64(1<<62)
	for range candidates {
		r := <-ch
		if r.ms >= 0 && r.ms < bestMs {
			best, bestMs = r.ip, r.ms
		}
	}
	if best != "" {
		log.Printf("echgate: %s 选用 %s（%dms，候选 %d 个）", sni, best, bestMs, len(candidates))
	}
	return best, best != ""
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

func splitList(s string) []string {
	out := make([]string, 0, 4)
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
