package lovehan1me.core.platform

import java.util.UUID

internal actual fun randomUUIDString(): String = UUID.randomUUID().toString()
