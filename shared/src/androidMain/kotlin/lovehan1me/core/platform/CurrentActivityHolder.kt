package lovehan1me.core.platform

import android.app.Activity
import java.lang.ref.WeakReference

/** P6d-4E：当前 resumed Activity 引用（:app HanimeApplication.onActivityResumed 注入） */
object CurrentActivityHolder {
    private var ref: WeakReference<Activity?> = WeakReference(null)

    @Synchronized
    fun set(activity: Activity?) {
        ref = WeakReference(activity)
    }

    val activity: Activity? get() = ref.get()
}
