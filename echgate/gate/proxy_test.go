package gate

import (
	"bufio"
	"context"
	"io"
	"net"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"
)

// startEcho 起一个 TCP echo 服务（隧道打通后用它验证双向字节流）。
func startEcho(t *testing.T) net.Listener {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) { defer c.Close(); _, _ = io.Copy(c, c) }(c)
		}
	}()
	return ln
}

// startConnectProxy 起一个极简 HTTP CONNECT 代理：接到 CONNECT 就把目标原样拨通并双向转发。
func startConnectProxy(t *testing.T) *url.URL {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				br := bufio.NewReader(c)
				line, err := br.ReadString('\n')
				if err != nil {
					return
				}
				fields := strings.Fields(line)
				if len(fields) < 2 || fields[0] != "CONNECT" {
					_, _ = io.WriteString(c, "HTTP/1.1 405 Method Not Allowed\r\n\r\n")
					return
				}
				target := fields[1]
				for { // 读掉剩余请求头
					l, err := br.ReadString('\n')
					if err != nil {
						return
					}
					if l == "\r\n" || l == "\n" {
						break
					}
				}
				up, err := net.Dial("tcp", target)
				if err != nil {
					_, _ = io.WriteString(c, "HTTP/1.1 502 Bad Gateway\r\n\r\n")
					return
				}
				defer up.Close()
				_, _ = io.WriteString(c, "HTTP/1.1 200 Connection Established\r\n\r\n")
				go func() { _, _ = io.Copy(up, br) }()
				_, _ = io.Copy(c, up)
			}(c)
		}
	}()
	return &url.URL{Scheme: "http", Host: ln.Addr().String()}
}

// startSocks5Proxy 起一个无认证的 SOCKS5 代理（只实现 CONNECT）。
func startSocks5Proxy(t *testing.T) *url.URL {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				hdr := make([]byte, 2)
				if _, err := io.ReadFull(c, hdr); err != nil {
					return
				}
				if _, err := io.ReadFull(c, make([]byte, int(hdr[1]))); err != nil {
					return
				}
				_, _ = c.Write([]byte{0x05, 0x00}) // 选无认证
				req := make([]byte, 4)
				if _, err := io.ReadFull(c, req); err != nil {
					return
				}
				var host string
				switch req[3] {
				case 0x01:
					b := make([]byte, 4)
					if _, err := io.ReadFull(c, b); err != nil {
						return
					}
					host = net.IP(b).String()
				case 0x03:
					l := make([]byte, 1)
					if _, err := io.ReadFull(c, l); err != nil {
						return
					}
					b := make([]byte, int(l[0]))
					if _, err := io.ReadFull(c, b); err != nil {
						return
					}
					host = string(b)
				case 0x04:
					b := make([]byte, 16)
					if _, err := io.ReadFull(c, b); err != nil {
						return
					}
					host = net.IP(b).String()
				}
				pb := make([]byte, 2)
				if _, err := io.ReadFull(c, pb); err != nil {
					return
				}
				port := int(pb[0])<<8 | int(pb[1])
				up, err := net.Dial("tcp", net.JoinHostPort(host, strconv.Itoa(port)))
				if err != nil {
					_, _ = c.Write([]byte{0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
					return
				}
				defer up.Close()
				_, _ = c.Write([]byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
				go func() { _, _ = io.Copy(up, c) }()
				_, _ = io.Copy(c, up)
			}(c)
		}
	}()
	return &url.URL{Scheme: "socks5", Host: ln.Addr().String()}
}

func withUpstreamProxy(t *testing.T, u *url.URL) {
	t.Helper()
	prev := upstreamProxy
	upstreamProxy = u
	t.Cleanup(func() { upstreamProxy = prev })
}

// 打完隧道后应能双向收发（证明 CONNECT 协商 + 后续字节未被缓冲吞掉）。
func assertEchoRoundTrip(t *testing.T, conn net.Conn) {
	t.Helper()
	msg := []byte("ping-through-proxy")
	if _, err := conn.Write(msg); err != nil {
		t.Fatalf("write: %v", err)
	}
	buf := make([]byte, len(msg))
	if _, err := io.ReadFull(conn, buf); err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(buf) != string(msg) {
		t.Fatalf("echo mismatch: %q", buf)
	}
}

func TestDialThroughHTTPConnectProxy(t *testing.T) {
	echo := startEcho(t)
	withUpstreamProxy(t, startConnectProxy(t))

	host, port, _ := net.SplitHostPort(echo.Addr().String())
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	conn, err := dialThroughProxy(ctx, host, port)
	if err != nil {
		t.Fatalf("dialThroughProxy: %v", err)
	}
	defer conn.Close()
	assertEchoRoundTrip(t, conn)
}

func TestDialThroughSocks5Proxy(t *testing.T) {
	echo := startEcho(t)
	withUpstreamProxy(t, startSocks5Proxy(t))

	_, port, _ := net.SplitHostPort(echo.Addr().String())
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	// SOCKS5 走域名分支：用 localhost 让代理按名字 CONNECT。
	conn, err := dialThroughProxy(ctx, "localhost", port)
	if err != nil {
		t.Fatalf("dialThroughProxy(socks5): %v", err)
	}
	defer conn.Close()
	assertEchoRoundTrip(t, conn)
}

func TestProxyModeSkipsECHAndCFIps(t *testing.T) {
	withUpstreamProxy(t, &url.URL{Scheme: "http", Host: "127.0.0.1:1"})
	// DoH 指到不可达的本地端口：让 lookupAWithCNAME 立刻失败返回空（不引外部网络，也不挂测试）。
	prevDoh, prevTimeout := dohURL, connTimeout
	dohURL, connTimeout = "http://127.0.0.1:1/resolve", 500*time.Millisecond
	t.Cleanup(func() { dohURL, connTimeout = prevDoh, prevTimeout })

	p := probePlan("hanime1.me")
	if p.useECH {
		t.Fatal("代理模式必须关 ECH")
	}
	if len(p.ips) != 1 || p.ips[0] != proxyDialPlaceholder {
		t.Fatalf("代理模式不应使用 CF 候选 IP，ips=%v", p.ips)
	}
}
