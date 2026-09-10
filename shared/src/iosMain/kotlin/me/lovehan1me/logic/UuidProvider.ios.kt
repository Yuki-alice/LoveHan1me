package me.lovehan1me.logic

import platform.Foundation.NSUUID

internal actual fun randomUUIDString(): String = NSUUID().UUIDString
