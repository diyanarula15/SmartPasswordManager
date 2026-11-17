package com.example.group4app.security

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.WindowManager
import java.io.File

/** Screenshot / screen-recording / recents-thumbnail protection (FLAG_SECURE). */
object ScreenSecurity {
    /** Debug-only QA escape hatch: `adb shell run-as <pkg> touch files/qa_allow_screenshots`. */
    const val QA_ALLOW_SCREENSHOTS_FILE = "qa_allow_screenshots"

    /** Call in onCreate before setContent. */
    fun apply(activity: Activity) {
        if (screenshotsAllowedForQa(activity)) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }

    /** Only ever true on a debuggable build whose private files dir holds the QA flag file. */
    fun screenshotsAllowedForQa(activity: Activity): Boolean {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return debuggable && File(activity.filesDir, QA_ALLOW_SCREENSHOTS_FILE).exists()
    }
}
