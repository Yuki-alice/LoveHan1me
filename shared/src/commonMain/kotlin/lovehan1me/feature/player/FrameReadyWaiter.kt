package lovehan1me.feature.player

/**
 * 等播放器**真的把目标时刻的帧画出来**。
 *
 * ## 为什么必须等，而不能 seek 完就抓
 * 三端能拿到的取帧 API（桌面 mediamp `FramePreview`、Android mpv `screenshot-raw`、
 * Android Exo `PixelCopy`）**全都抓"当前已经显示/渲染出来的那一帧"**，而不是"解码到某一时刻"。
 * `seekTo` 是异步的：命令返回时画面往往还是旧帧。**抓早了就会把上一帧写进 GIF** ——
 * 表现是录出来的 GIF 整体滞后，而且因为每帧都滞后，肉眼不容易定位。
 *
 * ## 为什么做成注入时间的纯逻辑
 * 这是唯一有真实"时序"的环节（轮询、超时、容差），也最容易写成"睡一个魔法值了事"。
 * 把 `nowMs` / `delayMs` 注入后，它就能在 desktopTest 里被完整覆盖 ——
 * 而平台侧只剩"调 seek、读属性、抓帧"三件直白的事。
 *
 * 判定"帧已就绪"用两个条件：位置进入了 [toleranceMs] 容差内，且引擎报告不在 seek 中。
 * 之后还要再等 [settleMs]：mpv 的 `screenshot-raw` 与 Android 的 `PixelCopy` 都读**显示侧**缓冲，
 * 位置属性更新通常早于画面真正呈现。
 */
internal class FrameReadyWaiter(
    private val toleranceMs: Long = 250L,
    private val timeoutMs: Long = 2_000L,
    private val pollMs: Long = 40L,
    private val settleMs: Long = 120L,
    private val nowMs: () -> Long,
    private val delayMs: suspend (Long) -> Unit,
) {
    /**
     * @param targetMs 想抓的时刻
     * @param positionProvider 当前播放位置（引擎的 state）
     * @param seekingProvider 引擎是否仍在 seek（不支持该属性的引擎传 false）
     * @return true 表示可以安全抓帧；false 表示已超时（调用方应当放弃这一帧而不是抓旧帧）
     */
    suspend fun await(
        targetMs: Long,
        positionProvider: () -> Long,
        seekingProvider: () -> Boolean = { false },
    ): Boolean {
        val deadline = nowMs() + timeoutMs
        while (true) {
            val position = positionProvider()
            val ready = !seekingProvider() &&
                position >= targetMs - toleranceMs &&
                position <= targetMs + toleranceMs
            if (ready) {
                if (settleMs > 0L) delayMs(settleMs)
                return true
            }
            if (nowMs() >= deadline) return false
            delayMs(pollMs)
        }
    }
}
