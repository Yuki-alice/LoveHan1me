package lovehan1me.core.util

import lovehan1me.core.platform.currentEpochMillis

/**
 * 冷启动分段埋点（M5-2）。
 *
 * ## 只打日志，不接上报
 * 本项目没有后端，"上报"既无去处也无必要 —— 埋点的用途只有一个：
 * **回答"这次冷启动慢在哪一段"**。所以每段只往 [LogUtil] 打一条带毫秒数的日志
 * （Android 走 logcat，桌面走 stdout），首帧后再打一行汇总。
 *
 * ## 分段名（三端统一，便于横向比对）
 * `application` → `datastore` → `settings` → `language` → `download-queue` →
 * `coil-register` → `first-frame`；另有一条 `coil` = **首次构造 ImageLoader**
 * （图片管线真正就绪，它是懒加载，通常晚于首帧，属预期）。
 *
 * ## 用法
 * 入口第一行 [begin]（冷启动时进程入口天然只走一次；重复调用不会重置起点），
 * 之后每完成一段调 [mark]；要把一段的耗时单独量出来可用 [segment] 或
 * [markSince]。首帧后调 [summary] 汇总。
 *
 * 线程：可从任意线程调用（内部加锁），因为各端入口线程不同（Android 主线程、
 * 桌面 AWT/Compose 线程）。
 */
object StartupTrace {

    private const val TAG = "Startup"

    private val lock = Any()
    private var originMillis = 0L
    private var started = false
    private val marks = LinkedHashMap<String, Long>()

    /**
     * 记录冷启动起点。重复调用**不会**覆盖起点 —— 入口若被走两次（如 Activity 重建），
     * 第二次不该把耗时算歪。
     */
    fun begin(source: String) {
        val first = synchronized(lock) {
            if (started) false else {
                started = true
                originMillis = currentEpochMillis()
                marks.clear()
                true
            }
        }
        if (first) LogUtil.i(TAG, "冷启动起点（$source）")
    }

    /**
     * 记录一段已完成：[name] 距起点多少毫秒。同名只记第一次（幂等，方便多处埋点）。
     *
     * @return 本次是否真的记录了（false = 未 [begin] 或该名字已记过）。
     *         首帧那类"只想打一次汇总"的调用点据此避免重复输出（例如 Activity 重建）。
     */
    fun mark(name: String): Boolean {
        val delta = synchronized(lock) {
            if (!started || marks.containsKey(name)) {
                null
            } else {
                val value = currentEpochMillis() - originMillis
                marks[name] = value
                value
            }
        } ?: return false
        LogUtil.i(TAG, "$name +${delta}ms")
        return true
    }

    /** 把 [sinceMillis] 起算的一段耗时记到 [name]（用于"自己掌握起点"的段落）。 */
    fun markSince(name: String, sinceMillis: Long) {
        val delta = currentEpochMillis() - sinceMillis
        LogUtil.i(TAG, "$name ${delta}ms")
        mark(name)
    }

    /** 跑一段并把耗时记为 [name]（异常照常抛出，但段落照样记录）。 */
    fun <T> segment(name: String, block: () -> T): T {
        val startedAt = currentEpochMillis()
        try {
            return block()
        } finally {
            markSince(name, startedAt)
        }
    }

    /**
     * 打一条"起点之外"的耗时（例如 Android 的"进程启动 → onCreate"）。
     * 它不进 [summary] 的段落表，只作对照：`Process.getStartUptimeMillis()` 与
     * 我们自己 [begin] 之间那段的真实开销（类加载、ContentProvider 初始化）都在里面。
     */
    fun logExternal(label: String, elapsedMillis: Long) {
        LogUtil.i(TAG, "$label ${elapsedMillis}ms（基准非本对象起点）")
    }

    /** 距 [begin] 的毫秒数（未 begin 返回 0）。 */
    fun elapsedMillis(): Long = synchronized(lock) {
        if (started) currentEpochMillis() - originMillis else 0L
    }

    /** 全部段落的一行汇总（首帧后打一次）。 */
    fun summary() {
        val (line, total) = synchronized(lock) {
            val total = if (started) currentEpochMillis() - originMillis else 0L
            marks.entries.joinToString(" | ") { "${it.key}+${it.value}ms" } to total
        }
        LogUtil.i(TAG, "冷启动汇总：$line | 合计 ${total}ms")
    }

    /** 单测用：清空状态（对象是全局单例，测试之间必须隔离）。 */
    internal fun resetForTest() {
        synchronized(lock) {
            originMillis = 0L
            started = false
            marks.clear()
        }
    }
}
