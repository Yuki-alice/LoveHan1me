#!/usr/bin/env python3
"""本地 HTTP CONNECT 转发代理（restrict_sim 的"代理可用"那一档）。

用途：受限形态下给调度器一条**真的能通**的让位路径，用来验证
`gateBlameAfterYield` 的定责与三态的"熔断/恢复"。

它只做一件事：接受 CONNECT host:port，拨号成功后把两端 socket 接起来原样转发。
不解密、不改写、不记录内容（项目红线：不经手内容、不收使用行为）。

    python3 fake_proxy.py [port]        # 默认 8899

⚠️ 未在本机执行过（本机是 Windows，本仓既有 phase5 脚本是 macOS/zsh 口径）。
   首次使用前先在目标机跑一次：`python3 fake_proxy.py 8899` 再
   `curl -x http://127.0.0.1:8899 https://hanime1.me/ -o /dev/null -w '%{http_code}'`。
"""

import select
import socket
import socketserver
import sys
import threading

BUFFER = 65536


def pump(a: socket.socket, b: socket.socket) -> None:
    try:
        while True:
            data = a.recv(BUFFER)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for s in (a, b):
            try:
                s.close()
            except OSError:
                pass


class ProxyHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        line = self.rfile.readline().decode("latin-1", "replace").strip()
        if not line:
            return
        method, target, _ = (line.split(" ") + ["", ""])[:3]
        if method.upper() != "CONNECT":
            # 只支持 CONNECT：让位路径全是 https，明文转发用不上。
            self.wfile.write(b"HTTP/1.1 405 Method Not Allowed\r\n\r\n")
            return
        host, _, port = target.rpartition(":")
        port = int(port or 443)
        try:
            upstream = socket.create_connection((host, port), timeout=15)
        except OSError as exc:
            print(f"[fake_proxy] CONNECT {target} failed: {exc}", flush=True)
            self.wfile.write(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
            return
        print(f"[fake_proxy] CONNECT {target} ok", flush=True)
        self.wfile.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        self.wfile.flush()
        self.connection.setblocking(False)
        upstream.setblocking(False)
        # 非阻塞双向转发：任意一端 EOF 就整体收摊。
        socks = [self.connection, upstream]
        try:
            while True:
                readable, _, errored = select.select(socks, [], socks, 60)
                if errored:
                    break
                if not readable:
                    break
                for sock in readable:
                    try:
                        data = sock.recv(BUFFER)
                    except (BlockingIOError, OSError):
                        data = None
                    if not data:
                        return
                    other = upstream if sock is self.connection else self.connection
                    try:
                        other.sendall(data)
                    except OSError:
                        return
        finally:
            for s in socks:
                try:
                    s.close()
                except OSError:
                    pass


class ThreadedProxy(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
    print(f"[fake_proxy] listening on 127.0.0.1:{port}", flush=True)
    with ThreadedProxy(("127.0.0.1", port), ProxyHandler) as server:
        server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
