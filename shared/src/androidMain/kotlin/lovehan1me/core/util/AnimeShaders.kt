package lovehan1me.core.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * mpv 的 CA 证书（`cacert.pem`）落盘。
 *
 * 阶段一②后 Anime4K shader 已迁到 `commonMain/composeResources/files/shaders/`
 * 三端共用（见 [lovehan1me.core.util.MpvShaders]），本对象只管证书。
 */
object AnimeShaders {

    fun copyCertAssets(context: Context): Int {
        return try {
            val assetName = "cacert.pem"
            val outputFile = File(context.filesDir, assetName)
            if (!outputFile.exists()) {
                val assetManager = context.assets
                assetManager.open(assetName).use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    }
                }
            }
            1
        } catch (e: IOException) {
            e.printStackTrace()
            -1
        }
    }

    fun getCert(context: Context): String {
        val certFile = File(context.filesDir, "cacert.pem")
        if (!certFile.exists()) {
            throw IllegalStateException("Root certificate file not found: $certFile")
        }
        return certFile.absolutePath
    }
}
