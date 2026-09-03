package io.github.daisukikaffuchino.han1meviewer.logic

import platform.Foundation.NSUUID

internal actual fun randomUUIDString(): String = NSUUID().UUIDString
