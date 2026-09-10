package lovehan1me.ui.player

/**
 * P5-1：跨平台播放器工厂入口。
 *
 * - androidMain：经共享层已有的 Context holder（[lovehan1me.logic.dao.Han1meDatabaseContext]）
 *   取 applicationContext，走既有 `PlaybackEngineFactory` 逻辑（含 Cast 包装）。
 * - desktopMain / iosMain：返回占位引擎（P5-2 接真引擎）。
 */
expect fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean = true,
): PlaybackEngine
