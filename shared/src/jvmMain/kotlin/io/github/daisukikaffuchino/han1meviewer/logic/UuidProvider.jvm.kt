package io.github.daisukikaffuchino.han1meviewer.logic

import java.util.UUID

internal actual fun randomUUIDString(): String = UUID.randomUUID().toString()
