package lovehan1me.feature.player

/**
 * 跨平台播放器工厂入口（Gate3-P1：配置注入版）。
 *
 * 引擎不再直读设置与网关单例，调用方显式传入：
 * - [network]：UA/代理/网关改写（实现由 `:shared` data 层提供）；
 * - [mpvOptions]：mpv 选项快照提供器（每次 load 取一次，用户改设置下个视频生效）。
 *
 * - androidMain：经 `Han1meDatabaseContext.appContext` 取 Context，走 `PlaybackEngineFactory`；
 * - 三端统一忽略 kernel（Gate3-P6 后 Android 也不再分叉，恒返 mediamp-exo），
 *   恒返本端唯一引擎；desktop 仍真用 [mpvOptions]，另两端为惰性参数（签名统一）。
 */
expect fun createPlaybackEngine(
    kernel: PlayerKernel,
    network: PlayerNetworkConfig,
    mpvOptions: PlayerMpvOptionsProvider,
): PlaybackEngine
