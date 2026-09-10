package lovehan1me.core.platform

import platform.Foundation.NSUUID

internal actual fun randomUUIDString(): String = NSUUID().UUIDString
