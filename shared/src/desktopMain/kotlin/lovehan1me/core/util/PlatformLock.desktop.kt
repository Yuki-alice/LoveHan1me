package lovehan1me.core.util

actual typealias PlatformLock = Any

actual inline fun <T> PlatformLock.withLock(block: () -> T): T = synchronized(this, block)
