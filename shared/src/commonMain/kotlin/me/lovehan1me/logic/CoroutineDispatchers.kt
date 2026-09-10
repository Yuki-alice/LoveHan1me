package me.lovehan1me.logic

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 网络/IO 密集 flow 的调度器（P4b：commonMain 无 Dispatchers.IO）。
 *  - jvmMain（android+desktop）：Dispatchers.IO（与旧 :app 行为一致）
 *  - iosMain：Darwin 无 IO 专属池，用 Dispatchers.Default（语义差异见交付报告）
 */
internal expect val ioDispatcher: CoroutineDispatcher
