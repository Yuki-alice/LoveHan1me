package lovehan1me.ui.activity

import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import lovehan1me.BuildConfig
import lovehan1me.R
import lovehan1me.Res
import lovehan1me.copy_to_clipboard
import lovehan1me.crash_no_logs
import lovehan1me.ui.crash.CrashHandler
import lovehan1me.ui.screen.crash.CrashScreen
import lovehan1me.utils.ActivityManager
import lovehan1me.utils.SonnerToast
import lovehan1me.utils.toastText
import lovehan1me.utils.rememberCopyTextToClipboard
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
            // P6d-3-C3：回调内 toast 转 suspend getString，经 scope 桥接
            val scope = rememberCoroutineScope()
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
                    scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
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
