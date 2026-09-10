package lovehan1me.ui.screen.account

import java.io.File

internal actual fun readFileBytes(path: String): ByteArray? =
    runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()
