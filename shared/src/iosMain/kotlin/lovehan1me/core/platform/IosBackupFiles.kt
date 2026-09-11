@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import lovehan1me.core.util.LogUtil
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * 阶段一④：iOS 备份文件层（“写临时文件，再系统导出”）。
 *
 * iOS 的 `UIDocumentPickerViewController(forExporting:)` **要求先有一个已存在的
 * 文件**再让用户选位置——与 Android SAF / 桌面“先选位置再写内容”流程相反
 * （见阶段一进度报告 §4）。所以 iOS 侧语义统一为两步：
 *
 * - 导出：内容先落 `NSTemporaryDirectory()/lovehan1me-backup/`，再弹系统导出窗；
 *   用户确认后系统把文件搬到目标位置，我们删掉临时文件；
 * - 导入：系统选窗拿到沙盒外 URL → 经 security-scoped 拷贝进临时目录 →
 *   把**临时路径**交还给共享层，后续读写与别端无异。
 *
 * 配套的共享层零改动设计：
 * - `rememberBackupExportLauncher` 直接回显建议文件名当 uri（真导出发生在
 *   写时），`BackupManager.exportTo/importFrom` 与各 lists 的
 *   `writeBackupText/readBackupText` 流程原样复用；
 * - 全量备份的 `openBackupSink` 返回的 Sink 在 `close()` 时弹导出窗
 *   （`close` 非 suspend，只能 fire-and-forget；成功 toast 可能先于用户点确认，
 *   文件本身已安全落临时目录——见 [ExportOnCloseSink]）；
 * - lists 的 `writeBackupText` 是 suspend，直接挂起等用户确认/取消，
 *   返回值准确（取消 = false → UI 报导出失败，不吓人但诚实）。
 *
 * 文件读写走 NSData/NSFileManager（与 `IosDownloadWorkController` /
 * `AvatarImageIo.ios` 同风格），**不**经 okio `FileSystem.SYSTEM`
 * （iOS 侧无前例，不引入新不确定性）；okio 只用作内存 `Buffer` 中转。
 */
private const val TAG = "IosBackup"
private const val BACKUP_TEMP_DIR_NAME = "lovehan1me-backup"

/** 活跃的 picker 调用（持有 delegate + picker 防提前释放，回调后移除）。 */
private val activeBackupPickers = mutableSetOf<BackupPickerCall>()

private class BackupPickerCall(
    val picker: UIDocumentPickerViewController,
    var delegate: NSObject? = null,
    val tempPath: String,
    val onDone: ((Boolean) -> Unit)? = null,
)

// ── 临时目录 ──────────────────────────────────────────────

internal fun backupTempDir(): String = NSTemporaryDirectory() + BACKUP_TEMP_DIR_NAME + "/"

internal fun ensureBackupTempDir() {
    NSFileManager.defaultManager.createDirectoryAtPath(backupTempDir(), true, null, null)
}

internal fun sanitizeBackupFileName(raw: String, fallback: String = "backup.json"): String {
    var name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    if (name.isBlank()) name = fallback
    name = name.replace(Regex("[/\\\\\u0000]"), "_")
    if (name.length > 120) name = name.takeLast(120)
    return name
}

/** 写字节到临时目录，返回绝对路径；失败返回 null。陈旧临时文件顺手清理。 */
internal fun writeTempBackupBytes(fileName: String, bytes: ByteArray): String? {
    return runCatching {
        ensureBackupTempDir()
        val dir = backupTempDir()
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null)
            ?.filterIsInstance<String>()
            ?.filter { it != fileName }
            ?.forEach { NSFileManager.defaultManager.removeItemAtPath(dir + it, null) }
        val full = dir + fileName
        // 写经 AvatarImageIo.ios 已验证的 NSData 路径（同包 internal 复用）
        check(writeBytesAtPath(full, bytes)) { "writeBytesAtPath failed: $full" }
        full
    }.onFailure { LogUtil.e(TAG, "writeTempBackupBytes failed", it) }.getOrNull()
}

internal fun readBackupFileBytes(path: String): ByteArray? {
    return runCatching { readBytesAtPath(path) }
        .onFailure { LogUtil.e(TAG, "readBackupFileBytes failed: $path", it) }
        .getOrNull()
}

// ── 视图层：找顶层 VC 并弹窗 ───────────────────────────────

internal fun topViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.windows.mapNotNull { it as? UIWindow }
    val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    var top = window?.rootViewController ?: return null
    while (true) {
        top = (top as? UINavigationController)?.visibleViewController
            ?: (top as? UITabBarController)?.selectedViewController
            ?: top.presentedViewController
            ?: break
    }
    return top
}

internal fun runOnMain(block: () -> Unit) {
    if (NSThread.isMainThread()) block()
    else dispatch_async(dispatch_get_main_queue(), block)
}

// ── 导出 ──────────────────────────────────────────────────

/**
 * 弹系统导出窗。同步返回前只保证“窗已弹出”（fire-and-forget），
 * 结果经 [onDone] 异步回（用户确认 true / 取消 false）。
 */
internal fun presentBackupExportPicker(filePath: String, onDone: ((Boolean) -> Unit)? = null) {
    runOnMain {
        val nsUrl = NSURL.fileURLWithPath(filePath)
        val host = topViewController()
        if (host == null) {
            LogUtil.e(TAG, "presentBackupExportPicker: no view controller")
            onDone?.invoke(false)
            return@runOnMain
        }
        val picker = UIDocumentPickerViewController(
            forExportingURLs = listOf(nsUrl),
            asCopy = true,
        )
        val call = BackupPickerCall(picker = picker, tempPath = filePath, onDone = onDone)
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                // 系统已把文件搬到用户位置，临时文件删掉
                runCatching {
                    NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                }
                activeBackupPickers.remove(call)
                onDone?.invoke(true)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                activeBackupPickers.remove(call)
                onDone?.invoke(false)
            }
        }
        call.delegate = delegate
        picker.delegate = delegate
        activeBackupPickers.add(call)
        host.presentViewController(picker, true, null)
    }
}

/** suspend 版导出：挂起直到用户确认（true）或取消/失败（false）。 */
internal suspend fun presentBackupExportAndAwait(filePath: String): Boolean {
    // 同一时间只允许一笔导出等待（备份入口是顺序 UI，撞上直接失败不叠窗）
    if (activeBackupPickers.isNotEmpty()) {
        LogUtil.e(TAG, "presentBackupExportAndAwait: another picker active")
        return false
    }
    return suspendCancellableCoroutine { cont ->
        presentBackupExportPicker(filePath) { ok ->
            if (cont.isActive) cont.resume(ok)
        }
    }
}

// ── 导入 ──────────────────────────────────────────────────

/** 弹系统选窗；选到文件后拷贝进临时目录并回传临时路径，取消回传 null。 */
internal fun presentBackupImportPicker(onResult: (String?) -> Unit) {
    runOnMain {
        val host = topViewController()
        if (host == null) {
            LogUtil.e(TAG, "presentBackupImportPicker: no view controller")
            onResult(null)
            return@runOnMain
        }
        // 现代 forOpeningContentTypes API；UTI 用字符串标识取（public.json 等），
        // 不硬绑 UTType 的类属性名（各 KN 版本映射有差异，编译器会验）。
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(
                UTType.typeWithIdentifier("public.json"),
                UTType.typeWithIdentifier("public.plain-text"),
                UTType.typeWithIdentifier("public.data"),
            ),
        )
        picker.allowsMultipleSelection = false
        lateinit var call: BackupPickerCall
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                activeBackupPickers.remove(call)
                val url = didPickDocumentsAtURLs.filterIsInstance<NSURL>().firstOrNull()
                if (url == null) {
                    onResult(null)
                    return
                }
                onResult(copyPickedToTemp(url))
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                activeBackupPickers.remove(call)
                onResult(null)
            }
        }
        call = BackupPickerCall(picker = picker, tempPath = "", onDone = null)
        call.delegate = delegate
        picker.delegate = delegate
        activeBackupPickers.add(call)
        host.presentViewController(picker, true, null)
    }
}

/** 沙盒外 URL → security-scoped 拷贝进临时目录，返回临时路径（失败 null）。 */
private fun copyPickedToTemp(url: NSURL): String? {
    return runCatching {
        ensureBackupTempDir()
        val name = sanitizeBackupFileName(url.lastPathComponent ?: "import.json", "import.json")
        val dest = backupTempDir() + name
        val accessing = url.startAccessingSecurityScopedResource()
        try {
            NSFileManager.defaultManager.removeItemAtPath(dest, null)
            val destUrl = NSURL.fileURLWithPath(dest)
            val ok = NSFileManager.defaultManager.copyItemAtURL(url, destUrl, null)
            check(ok) { "copyItemAtURL failed: ${url.path}" }
            dest
        } finally {
            if (accessing) url.stopAccessingSecurityScopedResource()
        }
    }.onFailure { LogUtil.e(TAG, "copyPickedToTemp failed", it) }.getOrNull()
}

// ── okio 桥：内存 Buffer 中转（不碰 FileSystem.SYSTEM） ────

/** 全量备份写通道：内存攒，close() 落临时文件并弹导出窗（fire-and-forget）。 */
internal class ExportOnCloseSink(
    private val tempName: String,
) : Sink {
    private val buffer = Buffer()
    private var closed = false

    override fun write(source: Buffer, byteCount: Long) {
        check(!closed) { "Sink closed" }
        buffer.write(source, byteCount)
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        if (closed) return
        closed = true
        val path = writeTempBackupBytes(tempName, buffer.readByteArray())
        if (path != null) {
            presentBackupExportPicker(path)
        } else {
            LogUtil.e(TAG, "ExportOnCloseSink: temp write failed")
        }
    }
}

/** 全量备份读通道：一次性读进内存（备份文件百 KB 级，常驻无压力）。 */
internal class TempFileSource(path: String) : Source {
    private val buffer = Buffer()
    private var closed = false

    init {
        val bytes = readBackupFileBytes(path) ?: error("Unable to open backup file: $path")
        buffer.write(bytes)
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        check(!closed) { "Source closed" }
        return buffer.read(sink, byteCount)
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        closed = true
        buffer.clear()
    }
}
