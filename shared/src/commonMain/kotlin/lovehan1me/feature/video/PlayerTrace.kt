package lovehan1me.feature.video

import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock

/**
 * 播放器埋点（M5 体验打磨：**状态反馈 / 手势**两个方向的可观测性）。
 *
 * ## 为什么不用 StartupTrace
 * [lovehan1me.core.util.StartupTrace] 记的是**进程冷启动**那一条时间线（begin 只认第一次）。
 * 播放器需要的是**一次播放会话**的时间线：换片要重新起算、同一次播放里缓冲可能反复发生、
 * 还有大量"没有耗时、只要知道发生过"的事件（seek / 手势 / 面板）。所以单独一个对象，
 * 风格保持与 StartupTrace 一致：**只打日志、不接上报**（本项目没有后端）。
 *
 * ## 段落（有起止、关心耗时）
 * - `first-frame`：会话起点 → 首帧渲染（`hasRenderedFirstFrame` 置位），可跨端比"起播快慢"
 * - `buffering`：转圈起止（卡顿看门狗也算），**重复发生**，所以走 [spanStart]/[spanEnd] 而不是 [mark]
 *
 * ## 起播链路 marks（A-1：此前会话从简介到货起算，watch 等待全程盲区）
 * 会话起点 = 进详情页（`begin("video:"+code)`）；之后按发生顺序：
 * `engine-create-start/end`（引擎同步构造）→ `fetch-start/end`（watch 请求 + 解析）
 * → `video-info-ready`（简介到货）→ `load-called`（直链到手调引擎）→ `first-frame`。
 * summary 里缺了哪一段，哪一段就是卡点——先看这个再动刀。
 *
 * ## 事件（只关心发生过，带细节）
 * `play-click` / `error` / `retry` / `seek` / `seek-by` / `gesture` / `panel`
 *
 * ## 埋点落位（按 compose-state-and-effects：**谁拥有状态，谁负责副作用**）
 * - 会话起点 / 首帧 / 缓冲 / 错误 / 重试 / seek → 状态都在屏幕边界（VideoRouteHostScreen），
 *   用 `LaunchedEffect(语义输入)` 埋，跟着状态走、自动去重；
 * - 手势 / 侧栏面板 → 这两个是 VideoPlayerUi 的**局部 UI 状态**（不值得上提到 ViewModel），
 *   就在 UI 里用 `LaunchedEffect(...)` 埋，避免在屏幕边界堆一对"镜像状态"。
 *
 * 线程：可从任意线程调用（内部加锁），与 StartupTrace 同理。
 */
object PlayerTrace {

    private const val TAG = "PlayerTrace"

    private val lock = PlatformLock()
    private var originMillis = 0L
    private var sessionKey = ""
    private var started = false
    private val marks = LinkedHashMap<String, Long>()
    private val spans = HashMap<String, Long>()

    /**
     * 开一次播放会话。**换片即重置**（[sessionKey] 变化才重置，同片重组不会）。
     * 只用来给日志做前缀和起算点，不参与任何业务判断。
     */
    fun begin(sessionKey: String) {
        val changed = lock.withLock {
            if (started && this.sessionKey == sessionKey) {
                false
            } else {
                started = true
                this.sessionKey = sessionKey
                originMillis = currentEpochMillis()
                marks.clear()
                spans.clear()
                true
            }
        }
        if (changed) LogUtil.i(TAG, "会话开始：$sessionKey")
    }

    /** 记录距会话起点多少毫秒；同名只记第一次（幂等，多处埋点也不会刷屏）。 */
    fun mark(name: String): Boolean {
        val delta = lock.withLock {
            if (!started || marks.containsKey(name)) {
                null
            } else {
                val value = currentEpochMillis() - originMillis
                marks[name] = value
                value
            }
        } ?: return false
        LogUtil.i(TAG, "[$sessionKey] $name +${delta}ms")
        return true
    }

    /** 一次性事件（无耗时语义），[detail] 直接拼进日志。 */
    fun event(name: String, detail: String = "") {
        val delta = lock.withLock { if (started) currentEpochMillis() - originMillis else null }
        val at = delta?.let { " +${it}ms" } ?: ""
        LogUtil.i(TAG, "[$sessionKey] $name${if (detail.isBlank()) "" else ": $detail"}$at")
    }

    /** 开始一段（如缓冲）。同名重复调用以最后一次为准。 */
    fun spanStart(name: String) {
        lock.withLock { spans[name] = currentEpochMillis() }
        LogUtil.i(TAG, "[$sessionKey] $name 开始")
    }

    /** 结束一段并打出耗时；没有对应的 [spanStart] 时静默返回（例如刚进页面时的 false）。 */
    fun spanEnd(name: String) {
        val elapsed = lock.withLock {
            val from = spans.remove(name) ?: return
            currentEpochMillis() - from
        }
        LogUtil.i(TAG, "[$sessionKey] $name ${elapsed}ms")
    }

    /** 一行汇总（离开播放页时打）。 */
    fun summary() {
        val (line, total) = lock.withLock {
            val total = if (started) currentEpochMillis() - originMillis else 0L
            marks.entries.joinToString(" | ") { "${it.key}+${it.value}ms" } to total
        }
        LogUtil.i(TAG, "[$sessionKey] 汇总：$line | 合计 ${total}ms")
    }

    /** 单测用：清空状态（全局单例，测试之间必须隔离）。 */
    internal fun resetForTest() {
        lock.withLock {
            originMillis = 0L
            sessionKey = ""
            started = false
            marks.clear()
            spans.clear()
        }
    }
}
