package me.lovehan1me.logic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// iOS（Darwin）没有 IO 专属线程池，Dispatchers.IO 在 native 不可用 → 用 Default
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
