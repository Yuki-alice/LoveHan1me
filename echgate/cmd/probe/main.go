// Command probe：QUIC/HTTP-3 动工判据的前置实测工具（B5-1）。
//
// ## 它回答什么问题
//
// 在把 quic-go 接进网关（B5-2）之前，用最小代价回答三个问题：
//
//  1. UDP 443 可达率 —— 对目标 IP 做 QUIC 握手，成功即"这条路走得通"。
//     判据（D1 已确认）：明显低于 98% ⇒ QUIC 不做。
//  2. h2 vs h3 的握手 / 取小资源耗时对比（与 T1/T2 同方向，非同一指标）。
//  3. ECH 能不能经 QUIC 用（U3 残留：crypto/tls 的 QUICConn 是否消费
//     EncryptedClientHelloConfigList）。
//
// ## 为什么是独立命令而不是网关 flag
//
// 本工具只服务"做/不做"决策。quic-go 只被本包 import —— gate 包与
// gomobile 绑定（只绑 lovehan1me/echgate/gate）、桌面 CLI 的产物因此零变化；
// 决策若"不做"，依赖原样移除即可。B5-2 真动工时再把能力收进网关主线。
//
// ## 用法
//
//	cd echgate
//	go run ./cmd/probe -sni hanime1.me -ech \
//	  -ips 172.64.229.154,162.159.0.1,108.162.192.1,172.64.33.1,104.19.0.1 \
//	  -fetch /cdn-cgi/trace
//
// 模式（每个 IP × 模式 × -n 次握手；-fetch 时每格再取一次小资源）：
//
//	h2        TCP+TLS 握手（ALPN h2,http/1.1）—— 现状基线
//	h3        QUIC 握手（ALPN h3）—— 候选路径
//	h3-clear  QUIC 握手但不带 ECH —— 对照组（定位"是 QUIC 不通还是 ECH 不通"）
//
// -ech 开启时，h2/h3 两个模式带 ECHConfigList（后缀 +ech）。
// IP 之间并行、同一 IP 内各格串行（保证同格延迟样本不被并行干扰）。
package main

import (
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"
	"sync"
	"time"
)

type config struct {
	ips       []string
	sni       string
	port      int
	n         int
	timeout   time.Duration
	useECH    bool
	echDomain string
	fetchPath string
	asJSON    bool
}

func main() {
	ipsFlag := flag.String("ips", "", "目标 IP 列表（逗号分隔；原样使用，不做 DNS）")
	sniFlag := flag.String("sni", "", "TLS ServerName（SNI）")
	portFlag := flag.Int("port", 443, "目标端口")
	nFlag := flag.Int("n", 10, "每个（IP × 模式）的握手次数")
	timeoutFlag := flag.Duration("timeout", 6*time.Second, "单次握手/取资源超时上限")
	echFlag := flag.Bool("ech", false, "启用 ECH：经 DoH 拉取 ECHConfigList 并带进 h2/h3 握手")
	echB64Flag := flag.String("ech-b64", "", "直接提供 ECHConfigList base64（跳过 DoH）")
	echDomainFlag := flag.String("ech-domain", "cloudflare-ech.com", "ECH 公钥配置来源域")
	dohFlag := flag.String("doh", "https://dns.alidns.com/resolve", "DoH 端点（JSON 格式，用于取 ECH 配置）")
	fetchFlag := flag.String("fetch", "", "可选路径（如 /cdn-cgi/trace）：每格补一次小资源 GET")
	jsonFlag := flag.Bool("json", false, "以 JSON 输出（默认人读表格）")
	flag.Parse()

	cfg := config{
		ips:       splitList(*ipsFlag),
		sni:       *sniFlag,
		port:      *portFlag,
		n:         *nFlag,
		timeout:   *timeoutFlag,
		useECH:    *echFlag || *echB64Flag != "",
		echDomain: *echDomainFlag,
		fetchPath: *fetchFlag,
		asJSON:    *jsonFlag,
	}
	if len(cfg.ips) == 0 || cfg.sni == "" {
		fmt.Fprintln(os.Stderr, "用法：probe -ips <ip,ip,...> -sni <name> [-ech] [-fetch /path] [-n 10] [-json]")
		flag.PrintDefaults()
		os.Exit(2)
	}
	if cfg.n < 1 {
		log.Fatalf("probe: -n 必须 ≥ 1（收到 %d）", cfg.n)
	}
	if cfg.port < 1 || cfg.port > 65535 {
		log.Fatalf("probe: -port 非法：%d", cfg.port)
	}
	if cfg.fetchPath != "" && !strings.HasPrefix(cfg.fetchPath, "/") {
		log.Fatalf("probe: -fetch 须以 / 开头（收到 %q）", cfg.fetchPath)
	}

	// ECH 配置：优先 -ech-b64，其次 DoH。失败即退出 —— 没有配置就测不了 PoC，
	// 静默降级会产出"看起来成功"的假数据。
	var ech []byte
	var echInfo *ECHInfo
	if cfg.useECH {
		if *echB64Flag != "" {
			b, err := base64.StdEncoding.DecodeString(strings.TrimSpace(*echB64Flag))
			if err != nil {
				log.Fatalf("probe: -ech-b64 不是合法 base64：%v", err)
			}
			ech = b
			echInfo = &ECHInfo{Source: "flag", Bytes: len(b)}
		} else {
			b, source, err := fetchECHConfig(*dohFlag, cfg.echDomain, cfg.timeout)
			if err != nil {
				log.Fatalf("probe: 取 ECH 配置失败：%v", err)
			}
			ech = b
			echInfo = &ECHInfo{Source: source, Bytes: len(b)}
		}
		log.Printf("probe: ECH 配置 %d 字节（%s）", echInfo.Bytes, echInfo.Source)
	}

	rep := run(cfg, ech)
	rep.Time = time.Now().Format(time.RFC3339)
	rep.SNI = cfg.sni
	rep.Port = cfg.port
	rep.N = cfg.n
	rep.ECH = echInfo
	rep.FetchPath = cfg.fetchPath

	if cfg.asJSON {
		enc := json.NewEncoder(os.Stdout)
		enc.SetIndent("", "  ")
		if err := enc.Encode(rep); err != nil {
			log.Fatalf("probe: JSON 输出失败：%v", err)
		}
		return
	}
	printText(os.Stdout, rep)
}

// run 跑完全部目标（IP 之间并行），返回未填环境字段的 Report。
func run(cfg config, ech []byte) *Report {
	modes := modesFor(cfg.useECH)
	results := make([]IPResult, len(cfg.ips))
	var wg sync.WaitGroup
	for i, ip := range cfg.ips {
		wg.Add(1)
		go func(i int, ip string) {
			defer wg.Done()
			results[i] = measureIP(cfg, ech, modes, ip)
			log.Printf("probe: %s 完成", ip)
		}(i, ip)
	}
	wg.Wait()
	return &Report{Targets: results, Summary: summarize(results)}
}

// measureIP 对单个 IP 逐模式串行测量。
func measureIP(cfg config, ech []byte, modes []modeSpec, ip string) IPResult {
	res := IPResult{IP: ip}
	for _, m := range modes {
		mr := ModeResult{Mode: m.Name, N: cfg.n, UseECH: m.ECH}
		errs := make([]string, 0, 3)
		for i := 0; i < cfg.n; i++ {
			ms, echAccepted, err := handshake(m, ip, cfg.port, cfg.sni, ech, cfg.timeout)
			if err != nil {
				errs = appendErr(errs, err.Error())
				continue
			}
			mr.OK++
			mr.MS = append(mr.MS, ms)
			if m.ECH && echAccepted {
				mr.ECHOK++
			}
		}
		mr.Errors = errs
		if cfg.fetchPath != "" {
			f := fetchOnce(m, ip, cfg.port, cfg.sni, ech, cfg.fetchPath, cfg.timeout)
			mr.Fetch = &f
		}
		res.Modes = append(res.Modes, mr)
	}
	return res
}

// modesFor 组装本次运行的模式表（见文件头注释的命名约定）。
func modesFor(useECH bool) []modeSpec {
	if useECH {
		return []modeSpec{
			{Name: "h2+ech", Proto: "h2", ECH: true},
			{Name: "h3+ech", Proto: "h3", ECH: true},
			{Name: "h3-clear", Proto: "h3"},
		}
	}
	return []modeSpec{
		{Name: "h2", Proto: "h2"},
		{Name: "h3", Proto: "h3"},
	}
}

// appendErr 去重收集失败样本，最多 3 条（同类失败没必要刷满）。
func appendErr(errs []string, msg string) []string {
	if len(errs) >= 3 {
		return errs
	}
	for _, e := range errs {
		if e == msg {
			return errs
		}
	}
	return append(errs, msg)
}

// splitList 逗号分隔 → 去空条目（与 gate.SplitList 同语义，独立命令不 import gate）。
func splitList(s string) []string {
	out := make([]string, 0, 4)
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
