@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.core.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock

actual class PlatformLock actual constructor() {
    @PublishedApi
    internal val nsLock = NSLock()
}

actual inline fun <T> PlatformLock.withLock(block: () -> T): T {
    nsLock.lock()
    try {
        return block()
    } finally {
        nsLock.unlock()
    }
}
