package lovehan1me.logic

import java.util.UUID

internal actual fun randomUUIDString(): String = UUID.randomUUID().toString()
