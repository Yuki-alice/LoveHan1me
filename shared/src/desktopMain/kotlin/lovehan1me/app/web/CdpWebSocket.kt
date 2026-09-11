package lovehan1me.app.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.core.util.LogUtil
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import kotlin.random.Random

/**
 * 阶段一⑩：CDP 专用最小 WebSocket 客户端（stdlib only，无第三方 WS 栈）。
 *
 * 为什么手写：Ktor-OkHttp 的 WS 握手默认带 `Sec-WebSocket-Extensions:
 * permessage-deflate`，实测在本机 Edge headless 下建连后数秒内必掉线
 * （裸 socket 同样流程 5/5 存活，Ktor 5/5 死亡； offered压缩后服务端发压缩帧，
 * 而 Ktor 侧未装解码扩展——offer 关不掉，见 `install(WebSockets)` 抓包）。
 * CDP 只需要小文本帧，127 行自包含实现比调库更确定：
 * - 握手：HTTP Upgrade，要求 101（不谈任何扩展 → 服务端只发裸帧）；
 * - 发送：masked text 帧（7/16/64 位长度全实现）；
 * - 接收：text/binary（binary 当 text 解，CDP 不用）、ping 自动回 pong、
 *   close 帧即视为结束、continuation 帧拼接；
 * - 所有阻塞 IO 由调用方保证在 [Dispatchers.IO]（[CloudflareCdp.solve]
 *   本就在 IO 上下文）。
 */
internal class CdpWebSocket private constructor(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
) {
    companion object {
        private const val TAG = "CdpWebSocket"
        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA

        /** 建连并完成握手；非 101 直接抛（调用方转失败态）。 */
        suspend fun connect(url: String, timeoutMs: Int = 10_000): CdpWebSocket =
            withContext(Dispatchers.IO) {
                val uri = URI(url)
                val host = uri.host ?: error("bad ws url: $url")
                val port = if (uri.port == -1) 80 else uri.port
                val requestPath = (uri.rawPath ?: "/").ifEmpty { "/" } +
                    (uri.rawQuery?.let { "?$it" } ?: "")
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val key = Random.nextBytes(16)
                val request = buildString {
                    append("GET ").append(requestPath).append(" HTTP/1.1\r\n")
                    append("Host: ").append(host).append(':').append(port).append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: ")
                        .append(java.util.Base64.getEncoder().encodeToString(key)).append("\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val status = readHttpStatus(socket.getInputStream())
                if (!status.startsWith("HTTP/1.1 101")) {
                    runCatching { socket.close() }
                    error("WebSocket handshake failed: $status")
                }
                CdpWebSocket(socket, socket.getInputStream(), socket.getOutputStream())
            }

        private fun readHttpStatus(input: InputStream): String {
            val head = ByteArray(1024)
            var total = 0
            while (total < head.size) {
                val n = input.read(head, total, head.size - total)
                if (n < 0) break
                total += n
                val text = head.decodeToString(0, total)
                val firstLine = text.lineSequence().firstOrNull().orEmpty()
                if ("\r\n\r\n" in text) return firstLine
            }
            return ""
        }
    }

    /** 发文本帧（client→server 必须 mask）。 */
    fun sendText(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = Random.nextBytes(4)
        val header = when {
            payload.size < 126 -> byteArrayOf(0x81.toByte(), (0x80 or payload.size).toByte())
            payload.size < 65536 -> byteArrayOf(
                0x81.toByte(), 0xFE.toByte(),
                ((payload.size ushr 8) and 0xFF).toByte(), (payload.size and 0xFF).toByte(),
            )

            else -> {
                val sizeBytes = ByteArray(8)
                var s = payload.size.toLong()
                for (i in 7 downTo 0) {
                    sizeBytes[i] = (s and 0xFF).toByte()
                    s = s ushr 8
                }
                byteArrayOf(0x81.toByte(), 0xFF.toByte()) + sizeBytes
            }
        }
        synchronized(output) {
            output.write(header)
            output.write(mask)
            val masked = ByteArray(payload.size) { i ->
                (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            output.write(masked)
            output.flush()
        }
    }

    /**
     * 收一帧文本（阻塞 up to [timeoutMs]，超时返回 null）。
     * ping 自动回 pong 后继续等；close/流结束返回 null。
     */
    fun pollText(timeoutMs: Int = 10_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val fragments = mutableListOf<ByteArray>()
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) return null
            socket.soTimeout = remaining
            val frame = try {
                readFrame()
            } catch (e: java.net.SocketTimeoutException) {
                return null
            } catch (e: EOFException) {
                return null
            }
            if (frame == null) return null
            when (frame.opcode) {
                OPCODE_TEXT, OPCODE_BINARY, OPCODE_CONTINUATION -> {
                    fragments += frame.payload
                    if (frame.fin) {
                        val combined = fragments.fold(ByteArray(0)) { acc, b -> acc + b }
                        fragments.clear()
                        // binary 在 CDP 里不会出现，当 text 解
                        return combined.toString(Charsets.UTF_8)
                    }
                }

                OPCODE_PING -> sendPong(frame.payload)
                OPCODE_CLOSE -> return null
                else -> LogUtil.w(TAG, "unknown opcode ${frame.opcode}, skip")
            }
        }
    }

    fun close() {
        runCatching {
            synchronized(output) {
                output.write(byteArrayOf(0x88.toByte(), 0x80.toByte(), 0, 0, 0, 0))
                output.flush()
            }
        }
        runCatching { socket.close() }
    }

    private data class RawFrame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    private fun readFrame(): RawFrame? {
        val b0 = readByteOrNull() ?: return null
        val b1 = readByteOrNull() ?: return null
        val fin = (b0 and 0x80) != 0
        val opcode = b0 and 0x0F
        var length = (b1 and 0x7F).toLong()
        if (length == 126L) {
            length = ((readByteOrNull()?.toLong() ?: return null) shl 8) or
                (readByteOrNull()?.toLong() ?: return null)
        } else if (length == 127L) {
            length = 0
            repeat(8) {
                length = (length shl 8) or (readByteOrNull()?.toLong() ?: return null)
            }
        }
        // server→client 帧不 mask；若带 mask（不合规）则按协议解
        val masked = (b1 and 0x80) != 0
        val maskKey = if (masked) readExact(4) else null
        if (length > 8 * 1024 * 1024) error("frame too large: $length")
        val payload = readExact(length.toInt())
        if (maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return RawFrame(fin, opcode, payload)
    }

    private fun sendPong(payload: ByteArray) {
        runCatching {
            synchronized(output) {
                output.write(byteArrayOf(0x8A.toByte(), payload.size.toByte()))
                output.write(payload)
                output.flush()
            }
        }
    }

    private fun readByteOrNull(): Int? {
        val b = input.read()
        return if (b < 0) null else b
    }

    private fun readExact(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) throw EOFException("short frame body")
            off += r
        }
        return out
    }
}
