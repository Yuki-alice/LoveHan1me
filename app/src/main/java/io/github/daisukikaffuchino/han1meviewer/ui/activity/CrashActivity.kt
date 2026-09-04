package io.github.daisukikaffuchino.han1meviewer.ui.activity

import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import io.github.daisukikaffuchino.han1meviewer.BuildConfig
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.crash_no_logs
import io.github.daisukikaffuchino.han1meviewer.ui.crash.CrashHandler
import io.github.daisukikaffuchino.han1meviewer.ui.screen.crash.CrashScreen
import io.github.daisukikaffuchino.utils.ActivityManager
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.toastText
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class CrashActivity : BaseActivity() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val crashLog = intent.getStringExtra(CrashHandler.EXTRA_LOGS)
        val crashTimeMillis = System.currentTimeMillis()

        setHanimeContent {
            val noCrashLog = stringResource(Res.string.crash_no_logs)
            val report = remember(crashLog, crashTimeMillis, noCrashLog) {
                buildCrashReport(
                    crashLog = crashLog ?: noCrashLog,
                    crashTimeMillis = crashTimeMillis,
                    packageName = packageName,
                )
            }
            val copyTextToClipboard = rememberCopyTextToClipboard()
            val exitApp = {
                finishAffinity()
                Process.killProcess(Process.myPid())
                exitProcess(0)
            }

            BackHandler(onBack = exitApp)
            CrashScreen(
                crashReport = report,
                packageName = packageName,
                onCopyLog = {
                    copyTextToClipboard(report)
                    SonnerToast.success(toastText(R.string.copy_to_clipboard))
                },
                onRestartApp = { ActivityManager.restart(killProcess = true) },
                onExitApp = exitApp,
            )
        }
    }
}

private fun buildCrashReport(
    crashLog: String,
    crashTimeMillis: Long,
    packageName: String,
): String = buildString {
    val crashTime = Instant.ofEpochMilli(crashTimeMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    appendLine("App: Han1meViewer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Package: $packageName")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Crash time: $crashTime")
    appendLine()
    appendLine("====== beginning of crash ======")
    append(crashLog)
}
