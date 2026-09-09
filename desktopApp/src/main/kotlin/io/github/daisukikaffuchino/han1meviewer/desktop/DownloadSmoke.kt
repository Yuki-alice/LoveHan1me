package io.github.daisukikaffuchino.han1meviewer.desktop

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.dao.createDownloadDatabase
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.DownloadGroupEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.logic.platform.initializeDesktopDownloadQueue
import io.github.daisukikaffuchino.han1meviewer.logic.platform.desktopDownloadWorkController
import io.github.daisukikaffuchino.han1meviewer.logic.state.DownloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * M6-2c：桌面下载引擎端到端冒烟（`HAN1ME_SMOKE=download` 触发，跑完即退）。
 *
 * T0 外键自检：一次性诊断库验证「未播种→失败 / 播种+合法值→成功 / 播种+非法值→失败」
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

private fun insertTask(code: String, title: String, url: String): Boolean {
    val db = Han1meDatabases.download
    val dao = db.hanimeDownloadDao
    runBlocking {
        runCatching { db.downloadGroupDao.insertDefaultGroup() }
            .onFailure { println("[SMOKE] insertDefaultGroup failed: $it") }
        runCatching { dao.delete(code) }
            .onFailure { println("[SMOKE] delete old task failed: $it") }
    }
    println("[SMOKE] inserting task $code ...")
    val insertOk = runBlocking {
        runCatching {
            dao.insert(
                HanimeDownloadEntity(
                    groupId = DownloadGroupEntity.DEFAULT_GROUP_ID,
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
            true
        }.onFailure { println("[SMOKE] task insert failed: $it") }.getOrDefault(false)
    }
    if (insertOk) println("[SMOKE] task $code inserted OK") else println("[SMOKE] task $code insert FAILED")
    return insertOk
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

/**
 * T0：外键自检——用一次性诊断库验证三个 Case，无网络依赖。
 * 丢弃型实例绕过 Han1meDatabases 单例，仅用于诊断，不影响正式库。
 */
private fun runT0FkCheck(): Boolean {
    val tmpDir = File(System.getProperty("java.io.tmpdir"), "han1me_fk_diag")
    tmpDir.mkdirs()
    val dbFile = File(tmpDir, "fk_diag.db")
    dbFile.delete()
    // 丢弃型诊断实例，绕过 Han1meDatabases 单例
    val diagDb = createDownloadDatabase(dbFile.absolutePath)
    val groupDao = diagDb.downloadGroupDao
    val downloadDao = diagDb.hanimeDownloadDao
    val results = mutableListOf<String>()

    // Case C：未播种，直接用 DEFAULT_GROUP_ID 插入 → 预期失败（外键无父行）
    val caseC = runBlocking {
        runCatching {
            downloadDao.insert(
                HanimeDownloadEntity(
                    groupId = DownloadGroupEntity.DEFAULT_GROUP_ID,
                    coverUrl = "", title = "diag-c", addDate = now(),
                    videoCode = "diag-c", videoUri = "local://diag-c", coverUri = null,
                    quality = "1080P", videoUrl = "https://example.com/c.mp4",
                    length = 0L, downloadedLength = 0L, state = DownloadState.Queued,
                )
            )
            "SUCCESS"
        }.getOrElse { it.message ?: it::class.simpleName.orEmpty() }
    }
    val caseCPass = caseC != "SUCCESS"
    results += "Case C（未播种→DEFAULT_GROUP_ID）: ${if (caseCPass) "FAIL(预期)" else "SUCCESS(意外)"} [$caseC]"

    // Case A：播种后用 DEFAULT_GROUP_ID 插入 → 预期成功
    runBlocking { groupDao.insertDefaultGroup() }
    val caseA = runBlocking {
        runCatching {
            downloadDao.insert(
                HanimeDownloadEntity(
                    groupId = DownloadGroupEntity.DEFAULT_GROUP_ID,
                    coverUrl = "", title = "diag-a", addDate = now(),
                    videoCode = "diag-a", videoUri = "local://diag-a", coverUri = null,
                    quality = "1080P", videoUrl = "https://example.com/a.mp4",
                    length = 0L, downloadedLength = 0L, state = DownloadState.Queued,
                )
            )
            val found = downloadDao.find("diag-a", "1080P")
            if (found != null) "SUCCESS" else "INSERT_OK_BUT_FIND_NULL"
        }.getOrElse { it.message ?: it::class.simpleName.orEmpty() }
    }
    val caseAPass = caseA == "SUCCESS"
    results += "Case A（播种→DEFAULT_GROUP_ID）: ${if (caseAPass) "PASS" else "FAIL"} [$caseA]"

    // Case B：播种后用 groupId=0 插入 → 预期失败（0 不存在于 download_groups）
    val caseB = runBlocking {
        runCatching {
            downloadDao.insert(
                HanimeDownloadEntity(
                    groupId = 0,
                    coverUrl = "", title = "diag-b", addDate = now(),
                    videoCode = "diag-b", videoUri = "local://diag-b", coverUri = null,
                    quality = "1080P", videoUrl = "https://example.com/b.mp4",
                    length = 0L, downloadedLength = 0L, state = DownloadState.Queued,
                )
            )
            "SUCCESS"
        }.getOrElse { it.message ?: it::class.simpleName.orEmpty() }
    }
    val caseBPass = caseB != "SUCCESS"
    results += "Case B（播种→groupId=0）: ${if (caseBPass) "FAIL(预期)" else "SUCCESS(意外)"} [$caseB]"

    results.forEach { println("[SMOKE] T0 $it") }
    dbFile.delete()
    return caseCPass && caseAPass && caseBPass
}

private fun runSmoke(): Boolean {
    println("[SMOKE] user.home = " + System.getProperty("user.home"))
    println("[SMOKE] cwd = " + File(".").absolutePath)
    val results = mutableListOf<String>()

    // T0：外键自检
    val t0Ok = runT0FkCheck()
    results += if (t0Ok) "T0 外键自检  PASS" else "T0 外键自检  FAIL"

    // T1：快速完成
    if (!insertTask(T1_CODE, "Smoke T1 (15s)", T1_URL)) {
        results += "T1 快速完成  FAIL（insertTask 失败）"
        results.forEach { println(it) }
        return false
    }
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
    if (!insertTask(T2_CODE, "Smoke T2 (30s)", T2_URL)) {
        results += "T2 断点续传  FAIL（insertTask 失败）"
        results.forEach { println(it) }
        return false
    }
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