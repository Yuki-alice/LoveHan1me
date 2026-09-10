package lovehan1me.feature.player

/**
 * P5-1：跨平台播放器工厂入口。
 *
 * - androidMain：经共享层已有的 Context holder（[lovehan1me.data.database.dao.Han1meDatabaseContext]）
 *   取 applicationContext，走既有 `PlaybackEngineFactory` 逻辑。
 * - desktopMain / iosMain：返回占位引擎（P5-2 接真引擎）。
 *
 * 投屏 Cast 已按阶段一决策⑪移除，不再有 `allowCast` 包装。
 */
expect fun createPlaybackEngine(
    kernel: PlayerKernel,
): PlaybackEngine
