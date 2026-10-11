package gate

import (
	"bufio"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// proxyDialPlaceholder 是代理模式下 plan.ips 的占位值。
//
// 代理模式下 IP 由代理侧解析，CF 候选池无意义；但 dialUpstream 会按 plan.ips 逐个尝试，
// 空表即报错。放一个非空占位让循环恰好跑一次，真正的目标在 dialThroughProxy 里用 dialHost。
const proxyDialPlaceholder = "proxy"

// dialThroughProxy 经 [upstreamProxy] 建立到 host:port 的隧道连接。
//
// 支持 `http` / `https`（CONNECT）与 `socks5` / `socks5h`（RFC1928，含无认证与用户名密码两种）。
// 认证信息取自代理 URL 的 userinfo（`http://user:pass@host:port`）。
func dialThroughProxy(ctx context.Context, host, port string) (net.Conn, error) {
	if upstreamProxy == nil {
		return nil, errors.New("echgate: no upstream proxy configured")
	}
	d := &net.Dialer{Timeout: connTimeout}
	conn, err := d.DialContext(ctx, "tcp", upstreamProxy.Host)
	if err != nil {
		return nil, fmt.Errorf("echgate: dial proxy %s: %w", upstreamProxy.Host, err)
	}
	switch strings.ToLower(upstreamProxy.Scheme) {
	case "socks5", "socks5h":
		// 域名交给代理解析（socks5h 语义）；socks5 由代理按名字 CONNECT 同样安全。
		out, err := socks5Connect(conn, upstreamProxy, host, port)
		if err != nil {
			conn.Close()
			return nil, err
		}
		return out, nil
	default: // http / https
		out, err := httpConnect(conn, upstreamProxy, net.JoinHostPort(host, port))
		if err != nil {
			conn.Close()
			return nil, err
		}
		return out, nil
	}
}

// bufferedConn 让隧道连接继续从谈判时用的 bufio.Reader 读 —— 否则 bufio 预读走的字节会丢。
type bufferedConn struct {
	net.Conn
	r *bufio.Reader
}

func (c *bufferedConn) Read(b []byte) (int, error) { return c.r.Read(b) }

// httpConnect 在既有 TCP 连接上做一次 CONNECT 隧道握手。
//
// 手写状态行 + 头读取而非 http.ReadResponse：后者会挂一个 Body，
// 对 2xx CONNECT 关闭它会读到隧道 EOF，把刚建好的隧道关掉。
func httpConnect(conn net.Conn, proxyURL *url.URL, hostport string) (net.Conn, error) {
	var b strings.Builder
	fmt.Fprintf(&b, "CONNECT %s HTTP/1.1\r\nHost: %s\r\n", hostport, hostport)
	if u := proxyURL.User; u != nil {
		pass, _ := u.Password()
		token := base64.StdEncoding.EncodeToString([]byte(u.Username() + ":" + pass))
		fmt.Fprintf(&b, "Proxy-Authorization: Basic %s\r\n", token)
	}
	b.WriteString("\r\n")
	if _, err := io.WriteString(conn, b.String()); err != nil {
		return nil, fmt.Errorf("echgate: proxy CONNECT write: %w", err)
	}

	br := bufio.NewReader(conn)
	statusLine, err := br.ReadString('\n')
	if err != nil {
		return nil, fmt.Errorf("echgate: proxy CONNECT read: %w", err)
	}
	fields := strings.Fields(statusLine)
	if len(fields) < 2 {
		return nil, fmt.Errorf("echgate: proxy CONNECT bad status line %q", strings.TrimSpace(statusLine))
	}
	if code, _ := strconv.Atoi(fields[1]); code != 200 {
		conn.Close()
		return nil, fmt.Errorf("echgate: proxy CONNECT rejected: %s", strings.TrimSpace(statusLine))
	}
	// 读完剩余响应头，直到空行。
	for {
		line, err := br.ReadString('\n')
		if err != nil {
			return nil, fmt.Errorf("echgate: proxy CONNECT header read: %w", err)
		}
		if line == "\r\n" || line == "\n" {
			break
		}
	}
	return &bufferedConn{Conn: conn, r: br}, nil
}

// socks5Connect 按 RFC1928 做 SOCKS5 问候 + 认证 + CONNECT。
//
// 只支持 CONNECT（本网关只需要隧道）；认证支持"无认证"与"用户名/密码"两种，
// 代理只提供其余方式时诚实报错，不静默降级。
func socks5Connect(conn net.Conn, proxyURL *url.URL, host, port string) (net.Conn, error) {
	var user, pass string
	useAuth := false
	if u := proxyURL.User; u != nil {
		user = u.Username()
		pass, _ = u.Password()
		useAuth = true
	}
	methods := []byte{0x00}
	if useAuth {
		methods = []byte{0x00, 0x02}
	}
	if _, err := conn.Write(append([]byte{0x05, byte(len(methods))}, methods...)); err != nil {
		return nil, err
	}
	reply := make([]byte, 2)
	if _, err := io.ReadFull(conn, reply); err != nil {
		return nil, err
	}
	if reply[0] != 0x05 {
		return nil, fmt.Errorf("echgate: socks5 bad version %d", reply[0])
	}
	switch reply[1] {
	case 0x00: // 无需认证
	case 0x02:
		if !useAuth {
			return nil, errors.New("echgate: socks5 proxy requires auth but none configured")
		}
		req := append([]byte{0x01, byte(len(user))}, []byte(user)...)
		req = append(req, byte(len(pass)))
		req = append(req, []byte(pass)...)
		if _, err := conn.Write(req); err != nil {
			return nil, err
		}
		if _, err := io.ReadFull(conn, reply); err != nil {
			return nil, err
		}
		if reply[1] != 0x00 {
			return nil, errors.New("echgate: socks5 auth failed")
		}
	default:
		return nil, fmt.Errorf("echgate: socks5 unsupported auth method %d", reply[1])
	}

	p, err := strconv.Atoi(port)
	if err != nil || p < 1 || p > 65535 {
		return nil, fmt.Errorf("echgate: socks5 bad port %q", port)
	}
	if len(host) == 0 || len(host) > 255 {
		return nil, fmt.Errorf("echgate: socks5 bad host %q", host)
	}
	req := append([]byte{0x05, 0x01, 0x00, 0x03, byte(len(host))}, []byte(host)...)
	req = append(req, byte(p>>8), byte(p&0xff))
	if _, err := conn.Write(req); err != nil {
		return nil, err
	}

	head := make([]byte, 4)
	if _, err := io.ReadFull(conn, head); err != nil {
		return nil, err
	}
	if head[1] != 0x00 {
		return nil, fmt.Errorf("echgate: socks5 connect failed (rep=%d)", head[1])
	}
	// 跳过 BND.ADDR/BND.PORT —— 长度随 ATYP 变。
	switch head[3] {
	case 0x01: // IPv4
		_, err = io.ReadFull(conn, make([]byte, 4+2))
	case 0x03: // 域名
		l := make([]byte, 1)
		if _, err = io.ReadFull(conn, l); err == nil {
			_, err = io.ReadFull(conn, make([]byte, int(l[0])+2))
		}
	case 0x04: // IPv6
		_, err = io.ReadFull(conn, make([]byte, 16+2))
	default:
		return nil, fmt.Errorf("echgate: socks5 bad atyp %d", head[3])
	}
	if err != nil {
		return nil, err
	}
	return conn, nil
}
