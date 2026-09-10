package lovehan1me.logic

import lovehan1me.Res

// jvmMain（android + desktop 共享）：compose resources 的 files 薄封装
internal actual suspend fun readAssetText(path: String): String? =
    runCatching { Res.readBytes("files/$path").decodeToString() }.getOrNull()

internal actual suspend fun readAssetBytes(path: String): ByteArray? =
    runCatching { Res.readBytes("files/$path") }.getOrNull()
