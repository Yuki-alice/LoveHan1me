package lovehan1me.feature.account

import java.io.File

internal actual fun readFileBytes(path: String): ByteArray? =
    runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()
