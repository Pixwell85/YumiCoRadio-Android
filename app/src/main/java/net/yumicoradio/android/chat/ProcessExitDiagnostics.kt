// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** Short user-facing interpretation of Android's process-exit reason codes. */
fun processExitSummary(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_EXIT_SELF -> "App exited normally"
    ApplicationExitInfo.REASON_SIGNALED -> "Process was terminated"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "Android reclaimed memory"
    ApplicationExitInfo.REASON_CRASH -> "App crash"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native app crash"
    ApplicationExitInfo.REASON_ANR -> "App not responding (ANR)"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "App startup failed"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Permission changed"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Excessive resource use"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "Android or the user requested a stop"
    ApplicationExitInfo.REASON_USER_STOPPED -> "App was force-stopped"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Required system process stopped"
    ApplicationExitInfo.REASON_FREEZER -> "Android froze the process"
    ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "App state changed"
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "App was updated"
    else -> "Unknown reason"
}

/**
 * Reads Android's newest local process-exit record. Nothing is persisted, logged, or transmitted.
 * Android 10 and older expose no equivalent public history API, so they return no line.
 */
fun readLastProcessExitSummary(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return readLastProcessExitSummaryApi30(context)
}

@RequiresApi(Build.VERSION_CODES.R)
private fun readLastProcessExitSummaryApi30(context: Context): String? = runCatching {
    val manager = context.getSystemService(ActivityManager::class.java)
    manager
        .getHistoricalProcessExitReasons(context.packageName, 0, 1)
        .firstOrNull()
        ?.let { processExitSummary(it.reason) }
}.getOrNull()
