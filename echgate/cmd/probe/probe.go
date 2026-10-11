package main

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/quic-go/quic-go"
	"github.com/quic-go/quic-go/http3"
)

// modeSpec 一次测量用的协议组合。
type modeSpec struct {
	Name  string // 显示名（h2 / h3 / h2+ech / h3-clear …）
	Proto string // "h2"（TCP+TLS）或 "h3"（QUIC）
	ECH   bool   // 是否带 ECHConfigList
}

func alpnFor(proto string) []string {
	if proto == "h3" {
		return []string{"h3"}
	}
	return []string{"h2", "http/1.1"}
}

// tlsConfigFor 与 gate 的出站配置同口径：默认 1.2 起；ECH 与 QUIC 都强制 1.3
// （ECH 只在 1.3 生效，gate 的 echCfg 亦如此；QUIC 本身强制 1.3）。
func tlsConfigFor(m modeSpec, sni string, ech []byte) *tls.Config {
	cfg := &tls.Config{ServerName: sni, MinVersion: tls.VersionTLS12, NextProtos: alpnFor(m.Proto)}
	if m.Proto == "h3" || m.ECH {
		cfg.MinVersion = tls.VersionTLS13
	}
	if m.ECH {
		cfg.EncryptedClientHelloConfigList = ech
	}
	return cfg
}

// handshake 做一次握手并返回耗时（ms）与“服务端是否接受 ECH”。
//
// ECHAccepted 取值自 tls.ConnectionState（crypto/tls）：为 true 表示 ECH
// 不只是发出去了、而且服务端确认处理了 —— 这是 U3 判据里“消费 configList”
// 的最强本地可观测证据（h2 直读；h3 经 quic.ConnectionState().TLS 直读）。
// h2 = TCP+TLS，h3 = QUIC（quic-go DialAddr）。
func handshake(m modeSpec, ip string, port int, sni string, ech []byte, timeout time.Duration) (int64, bool, error) {
	cfg := tlsConfigFor(m, sni, ech)
	addr := net.JoinHostPort(ip, strconv.Itoa(port))
	start := time.Now()

	if m.Proto == "h3" {
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		defer cancel()
		conn, err := quic.DialAddr(ctx, addr, cfg, &quic.Config{HandshakeIdleTimeout: timeout})
		if err != nil {
			return 0, false, err
		}
		ms := time.Since(start).Milliseconds()
		accepted := conn.ConnectionState().TLS.ECHAccepted
		_ = conn.CloseWithError(0, "")
		return ms, accepted, nil
	}

	d := &net.Dialer{Timeout: timeout}
	raw, err := d.Dial("tcp", addr)
	if err != nil {
		return 0, false, err
	}
	defer raw.Close()
	tc := tls.Client(raw, cfg)
	defer tc.Close()
	_ = tc.SetDeadline(time.Now().Add(timeout))
	if err := tc.Handshake(); err != nil {
		return 0, false, err
	}
	if p := tc.ConnectionState().NegotiatedProtocol; p != "h2" && p != "http/1.1" {
		return 0, false, fmt.Errorf("ALPN 意外：%q", p)
	}
	return time.Since(start).Milliseconds(), tc.ConnectionState().ECHAccepted, nil
}

// fetchOnce 握手后取一次小资源：验证"这条路不只是能握手，还能过数据"。
//
// 两种协议都把连接钉死到被测 IP（h2 经 DialTLSContext，h3 经 Transport.Dial），
// URL 主机名只用来定 SNI/Host —— 这样测的是"这条链路"，系统 DNS 不参与。
func fetchOnce(m modeSpec, ip string, port int, sni string, ech []byte, path string, timeout time.Duration) Fetch {
	cfg := tlsConfigFor(m, sni, ech)
	urlHost := sni
	if port != 443 {
		urlHost = net.JoinHostPort(sni, strconv.Itoa(port))
	}
	url := "https://" + urlHost + path

	var rt http.RoundTripper
	var release func()
	if m.Proto == "h3" {
		tr := &http3.Transport{
			TLSClientConfig: cfg,
			QUICConfig:      &quic.Config{HandshakeIdleTimeout: timeout},
			Dial: func(ctx context.Context, _ string, tlsCfg *tls.Config, c *quic.Config) (*quic.Conn, error) {
				return quic.DialAddr(ctx, net.JoinHostPort(ip, strconv.Itoa(port)), tlsCfg, c)
			},
		}
		rt = tr
		release = func() { _ = tr.Close() }
	} else {
		tr := &http.Transport{
			ForceAttemptHTTP2: true,
			TLSClientConfig:   cfg,
			DialTLSContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
				d := &net.Dialer{Timeout: timeout}
				raw, err := d.DialContext(ctx, "tcp", net.JoinHostPort(ip, strconv.Itoa(port)))
				if err != nil {
					return nil, err
				}
				tc := tls.Client(raw, cfg)
				if err := tc.HandshakeContext(ctx); err != nil {
					_ = raw.Close()
					return nil, err
				}
				return tc, nil
			},
		}
		rt = tr
		release = tr.CloseIdleConnections
	}
	defer release()

	client := &http.Client{Transport: rt, Timeout: 2 * timeout}
	start := time.Now()
	resp, err := client.Get(url)
	if err != nil {
		return Fetch{Err: err.Error()}
	}
	ttfb := time.Since(start).Milliseconds()
	n, _ := io.Copy(io.Discard, resp.Body)
	total := time.Since(start).Milliseconds()
	_ = resp.Body.Close()
	return Fetch{Proto: resp.Proto, Status: resp.StatusCode, TTFBMs: ttfb, TotalMs: total, Bytes: int(n)}
}

// ── ECH 配置（自包含，刻意不 import gate —— 见 cmd/probe 头部说明） ──

// probeDoHFallbacks 与 gate.echDohFallbacks 同值的兜底端点。
var probeDoHFallbacks = []string{
	"https://doh.pub/dns-query",
	"https://cloudflare-dns.com/dns-query",
}

// fetchECHConfig 依次尝试端点，返回 (ECHConfigList 字节, 命中的端点, error)。
func fetchECHConfig(doh, domain string, timeout time.Duration) ([]byte, string, error) {
	endpoints := make([]string, 0, len(probeDoHFallbacks)+1)
	if c := strings.TrimRight(doh, "?&"); c != "" {
		endpoints = append(endpoints, c)
	}
	for _, e := range probeDoHFallbacks {
		if !slices.Contains(endpoints, e) {
			endpoints = append(endpoints, e)
		}
	}

	var lastErr error
	for _, ep := range endpoints {
		b64, err := queryECHRecord(ep, domain, timeout)
		if err != nil {
			lastErr = err
			continue
		}
		b, err := base64.StdEncoding.DecodeString(b64)
		if err != nil {
			lastErr = err
			continue
		}
		return b, ep, nil
	}
	return nil, "", fmt.Errorf("全部 DoH 端点失败（最后一个：%v）", lastErr)
}

// queryECHRecord 查 HTTPS(type=65) 记录并取出 ech="…" 的 base64 值。
func queryECHRecord(doh, domain string, timeout time.Duration) (string, error) {
	sep := "?"
	if strings.Contains(doh, "?") {
		sep = "&"
	}
	u := fmt.Sprintf("%s%sname=%s&type=65", doh, sep, domain)
	req, err := http.NewRequest(http.MethodGet, u, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("accept", "application/dns-json")

	ctx, cancel := context.WithTimeout(context.Background(), timeout+4*time.Second)
	defer cancel()
	resp, err := http.DefaultClient.Do(req.WithContext(ctx))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return "", err
	}
	var out struct {
		Answer []struct {
			Type int    `json:"type"`
			Data string `json:"data"`
		} `json:"Answer"`
	}
	if err := json.Unmarshal(body, &out); err != nil {
		return "", err
	}
	for _, a := range out.Answer {
		if a.Type != 65 {
			continue
		}
		if b64 := extractECHParam(a.Data); b64 != "" {
			return b64, nil
		}
	}
	return "", fmt.Errorf("%s 的 HTTPS 应答里没有 ech=", domain)
}

// extractECHParam 与 gate.extractECHParam 同实现（HTTPS 记录的文本形式里取 ech="…"）。
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
