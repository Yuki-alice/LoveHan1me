package lovehan1me.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

lateinit var applicationContext: Context
    internal set

val application: Application
    get() = applicationContext as Application

val isX86_64Device: Boolean
    get() = Build.SUPPORTED_ABIS.any { it == "x86_64" }

val Context.activity: Activity?
    get() {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }


