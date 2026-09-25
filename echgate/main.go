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
// ## 两条通道（同一个端口，按请求方法分流）
//
// ### 主力：反向代理
//
//	GET http://127.0.0.1:<port>/path  +  X-Ech-Target: hanime1.me
//
// 网关还原为 `https://hanime1.me/path`，**由网关代为 TLS 握手**并按策略出站。
// 只有这条通道能用 ECH、也能把 SNI 换成 CNAME 真名（见上面 CDN77 那条）。
//
// ### 兜底：CONNECT 隧道
//
//	CONNECT host:443   —— 客户端把本网关当 http_proxy 用
//
// 客户端在隧道内**自己**做 TLS，SNI 对网关不可控、仍是明文，所以：
//   - 浏览器走它没意义（这正是最初"不做 CONNECT"的原因，结论仍成立）；
//   - 它只剩两件兜底价值：DoH 解析出未被污染的 IP + 逐 IP 拨号挑能连的。
//
// 于是它只作**第二选择**：主力通道失败后，调用方可以改用"真实 URL +
// http_proxy=127.0.0.1:<port>"，靠它绕开被污染的 DNS。详见 `gate.handleConnect`。
//
// 系统 WebView/WKWebView 两条都不走，用内置 hosts + 代理透传（见 CloudflareCdp 注释）。
//
// ## 与 gate 包的分工
// 出站策略/DoH/ECH 全在 `gate` 包（`gate.Start`），本文件只做 CLI flag 解析。
// 未来的 gomobile 绑定（Android `.aar` / iOS framework，进程内起服）
// 直接调 `gate.Start`，与本 CLI 同语义。
package main

import (
	"flag"
	"fmt"
	"log"
	"os/signal"
	"syscall"
	"time"

	"lovehan1me/echgate/gate"
)

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

	// 日志管道被启动方提前关闭时，写 stdout 不应以 SIGPIPE 杀死整个网关
	// （Kotlin 侧 EchGateProcess 已为全生命周期排空，这是纵深防御）。
	// 注意：Go 对 fd 1/2 的 SIGPIPE 默认致死，忽略后写操作返回 EPIPE 错误，
	// log 包丢弃该错误，服务继续。
	signal.Ignore(syscall.SIGPIPE)

	srv, err := gate.Start(gate.Config{
		ListenAddr: *listen,
		CfIPs:      gate.SplitList(*cfIPsFlag),
		CfHosts:    gate.SplitList(*cfHostsFlag),
		DohURL:     *doh,
		EchDomain:  *echDomain,
		EchB64:     *echB64Flag,
		CacheDir:   *cacheDir,
		TimeoutMs:  timeout.Milliseconds(),
	})
	if err != nil {
		log.Fatalf("echgate: listen failed: %v", err)
	}
	// stdout 合约：EchGateProcess.monitor 等 `LISTENING` 前缀判定就绪。
	fmt.Printf("LISTENING %s\n", srv.Addr())
	log.Printf("echgate: listening on %s (cf-ips=%v cf-hosts=%v)", srv.Addr(), *cfIPsFlag, *cfHostsFlag)
	select {}
}
