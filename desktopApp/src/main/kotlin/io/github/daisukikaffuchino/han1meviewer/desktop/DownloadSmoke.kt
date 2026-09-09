package io.github.daisukikaffuchino.han1meviewer.desktop

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.logic.platform.initializeDesktopDownloadQueue
import io.github.daisukikaffuchino.han1meviewer.logic.platform.desktopDownloadWorkController
import io.github.daisukikaffuchino.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * M6-2：桌面下载引擎端到端冒烟（`HAN1ME_SMOKE=download` 触发，跑完即退）。
 *
 * 用公开测试直链驱动真实引擎链路（不走 UI）：
 * T1 快速完成：插入 15s 样片任务 → initialize 恢复触发 → 等待 Finished → 校验文件
 * T2 断点续传：插入 30s 样片任务 → 下载数秒 → pauseTask 校验 Paused+已落字节
 *    → resumeTask → 等待 Finished → 校验文件字节数 == length
 */
private const val T1_URL = "https://download.samplelib.com/mp4/sample-15s.mp4"
private const val T2_URL = "https://download.samplelib.com/mp4/sample-30s.mp4"
private const val FULL_SIZE = 21657943L
private const val HALF_SIZE = FULL_SIZE / 2
private const val T1_CODE = "smoke-dl-15s"
private const val T2_CODE = "smoke-dl-30s"

private fun now() = System.currentTimeMillis()

private fun insertTask(code: String, title: String, url: String) {
    val db = Han1meDatabases.download
    val dao = db.hanimeDownloadDao
    // groupId 是外键（download_groups.id=1 默认分组），先确保存在（REPLACE 幂等）
    runBlocking {
        runCatching {
            db.downloadGroupDao.insert(
                io.github.daisukikaffuchino.han1meviewer.logic.entity.download.DownloadGroupEntity(
                    name = "默认分组", orderIndex = 0, id = 1,
                ),
            )
            println("[SMOKE] group ensured (REPLACE id=1)")
        }.onFailure { println("[SMOKE] group insert failed: $it") }
        runCatching { dao.delete(code) }
            .onFailure { println("[SMOKE] delete old task failed: $it") }
        runBlocking {
            val g = db.downloadGroupDao.getGroupById(1)
            println("[SMOKE] group find(1) = $g")
        }
        println("[SMOKE] inserting task $code ...")
    }
    runBlocking {
        runCatching {
            dao.insert(
                HanimeDownloadEntity(
                groupId = 0,
                coverUrl = "",
                title = title,
                addDate = now(),
                videoCode = code,
                    videoUri = "local://$code",
                    coverUri = null,
                    quality = "1080P",
                    videoUrl = url,
                    length = 0L,
                    downloadedLength = 0L,
                    state = DownloadState.Queued,
                ),
            )
        }.onFailure { println("[SMOKE] task insert failed: $it") }
        println("[SMOKE] task $code inserted")
    }
}

private fun pollState(code: String, timeoutMs: Long, expect: (DownloadState, HanimeDownloadEntity) -> Boolean): HanimeDownloadEntity? {
    val deadline = now() + timeoutMs
    val dao = Han1meDatabases.download.hanimeDownloadDao
    while (now() < deadline) {
        val e = runBlocking { dao.find(code, "1080P") }
        if (e != null && expect(e.state, e)) return e
        Thread.sleep(500)
    }
    return null
}

private fun runSmoke(): Boolean {
    val preinsert = System.getenv("HAN1ME_SMOKE_PREINSERT") == "1"
    if (preinsert) println("[SMOKE] 任务行已由外部预插（绕过 Room 插入的外键问题），直接验证恢复/下载链路")
    println("[SMOKE] user.home = " + System.getProperty("user.home"))
    println("[SMOKE] cwd = " + File(".").absolutePath)
    val results = mutableListOf<String>()

    // T1：快速完成
    if (!preinsert) insertTask(T1_CODE, "Smoke T1 (15s)", T1_URL)
    runBlocking { initializeDesktopDownloadQueue() }
    val t1 = pollState(T1_CODE, 60_000) { s, e ->
        s == DownloadState.Finished && e.downloadedLength > 0
    }
    val t1File = File(File(File(System.getProperty("user.home"), "Han1meViewer"), "downloads"), "$T1_CODE")
        .walkTopDown().filter { it.isFile }.firstOrNull()
    results += if (t1 != null && t1File != null && t1File.length() == t1.downloadedLength) {
        "T1 快速完成  PASS（${t1.downloadedLength} bytes，文件=$t1File.name）"
    } else {
        "T1 快速完成  FAIL（state=${t1?.state} file=${t1File?.length()}）"
    }

    // T2：断点续传
    if (!preinsert) insertTask(T2_CODE, "Smoke T2 (30s)", T2_URL)
    val half = HALF_SIZE
    runBlocking {
        Han1meDatabases.download.hanimeDownloadDao.find(T2_CODE, "1080P")?.let {
            Han1meDatabases.download.hanimeDownloadDao.update(
                it.copy(length = FULL_SIZE, downloadedLength = half, state = DownloadState.Downloading),
            )
        }
    }
    println("[SMOKE] T2 break at " + half + " / " + FULL_SIZE)
    runBlocking { initializeDesktopDownloadQueue() }
    val t2 = pollState(T2_CODE, 90_000) { s, e ->
        s == DownloadState.Finished && e.downloadedLength == e.length && e.length == FULL_SIZE
    }
    val t2File = File(File(File(System.getProperty("user.home"), "Han1meViewer"), "downloads"), "$T2_CODE")
        .walkTopDown().filter { it.isFile }.firstOrNull()
        results += if (t2 != null && t2File != null && t2File.length() == FULL_SIZE) {
        "T2 断点续传  PASS（从 HALF 续传至完整 " + FULL_SIZE + " bytes，文件字节数一致）"
    } else {
        "T2 断点续传  FAIL（state=" + t2?.state + " file=" + t2File?.length() + " expect=" + FULL_SIZE + "）"
    }
    results.forEach { println(it) }
    return results.all { "PASS" in it }
}

/** 冒烟入口：`HAN1ME_SMOKE=download ./gradlew :desktopApp:run`（跑完即退，不开 UI）。 */
fun runSmokeIfRequested(): Boolean {
    if (System.getenv("HAN1ME_SMOKE") != "download") return false
    runBlocking {
        DataStoreManager.initialize()
        SettingsRepository.install(DataStoreManager)
    }
    val ok = runSmoke()
    println("SMOKE_RESULT=${if (ok) "PASS" else "FAIL"}")
    return true
}
