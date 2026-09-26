package lovehan1me.core.util

// Gate3-P6：Android 的 mpv 内核已砍（产品决策 2026-09-24），mpv shader 落盘链路随之失效。
//
// ⚠️ 本文件**只**声明 `mpvShaderTargetDir()`：`materializeMpvShaders` 的 actual 在 jvmMain
// （android + desktop 共享同一份落盘实现），Android 再声明一次会撞 "Conflicting overloads"。
// 恒 null 正是想要的效果 —— 落盘实现拿到 null 目录即返回 null，调用方按契约降级 OFF
// （超分在 Android 走 mediamp-exo 的 `setVideoEffects`，见 `ExoSuperResolution`，
// 不经过 mpv 的 `glsl-shaders`）。语义与 iosMain 同形：本平台无 mpv shader 落盘，
// 不是漏实现。
actual suspend fun mpvShaderTargetDir(): String? = null
