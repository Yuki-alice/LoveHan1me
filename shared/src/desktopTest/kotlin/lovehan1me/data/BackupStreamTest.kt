package lovehan1me.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// B7 守卫：备份改走 okio 流式读写后，通道本身不能坏。
//
// 背景：BackupManager 原来是 `sink.writeUtf8(json.encodeToString(...))` /
// `json.decodeFromString(source.readUtf8())` —— 万级记录下那个整份 JSON 字符串是
// 峰值内存的大头。现在改 `encodeToSink` / `decodeFromSource`（配
// kotlinx-serialization-json-okio 桥接），不再经过字符串。
//
// 这里钉三件事：
// 1. 新通道能往返（换通道最容易换出个坏通道）；
// 2. prettyPrint 真的关掉了（体积是这个改动的主要收益来源）；
// 3. **旧备份必须还能读进来** —— 此前导出的文件是缩进过的，用户手里有存量。
//    这条是兼容性红线，不是性能项。
//
// 注：峰值内存本身要用 profiler 量，headless 断言不了，如实记为未测。
//
// 跑法：`:shared:desktopTest --tests "lovehan1me.data.BackupStreamTest" --offline`
class BackupStreamTest {

    @Serializable
    private data class Sample(
        val version: Int = 1,
        val items: List<String> = emptyList(),
    )

    private fun backupJson() = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun `流式写读可往返`() {
        val sample = Sample(version = 3, items = listOf("a", "b", "c"))
        // okio.Buffer 同时是 Sink 和 Source，且 close() 是 no-op，无需 use。
        val buffer = Buffer()

        backupJson().encodeToBufferedSink(sample, buffer)
        val readBack = backupJson().decodeFromBufferedSource<Sample>(buffer)

        assertEquals(sample, readBack, "encodeToBufferedSink / decodeFromBufferedSource 往返不等价")
    }

    @Test
    fun `prettyPrint关闭后输出不含缩进换行`() {
        val buffer = Buffer()
        backupJson().encodeToBufferedSink(Sample(items = listOf("a", "b")), buffer)

        val text = buffer.readUtf8()
        assertFalse(text.contains("\n"), "备份不该再有换行：$text")
        assertFalse(text.contains("  "), "备份不该再有缩进：$text")
    }

    @Test
    fun `旧版缩进过的备份仍能读进来`() {
        // 存量备份是 prettyPrint = true 导出的；关掉缩进不能把老文件读坏。
        val legacy = """
            {
                "version": 2,
                "items": [
                    "x",
                    "y"
                ]
            }
        """.trimIndent()

        val readBack = Buffer().also { it.writeUtf8(legacy) }
            .let { source -> backupJson().decodeFromBufferedSource<Sample>(source) }

        assertEquals(
            Sample(version = 2, items = listOf("x", "y")),
            readBack,
            "旧备份读不进来了：存量用户的备份文件会直接报废",
        )
    }
}
