package me.lovehan1me.logic

import me.lovehan1me.Res

// iosMain：compose resources 的 files 薄封装
internal actual suspend fun readAssetText(path: String): String? =
    runCatching { Res.readBytes("files/$path").decodeToString() }.getOrNull()

internal actual suspend fun readAssetBytes(path: String): ByteArray? =
    runCatching { Res.readBytes("files/$path") }.getOrNull()
