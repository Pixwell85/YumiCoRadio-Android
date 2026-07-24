// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * The daily upload allowance the server tracks per IP.
 *
 * Formatting lives here rather than in the UI so the arithmetic — which is the part that can be
 * wrong — is testable without a screen.
 */
data class UploadQuota(
    val used: Long = 0,
    val limit: Long = DEFAULT_LIMIT,
    val resetAt: Long = 0,
) {
    val remaining: Long get() = (limit - used).coerceAtLeast(0)

    /** 0f–1f, and never NaN: a server that reported a zero limit would otherwise divide by zero. */
    val fraction: Float get() = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)

    fun format(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    val summary: String get() = "${format(used)} / ${format(limit)} used today"

    /**
     * When the allowance comes back, in the phone's own locale and zone.
     *
     * The server counts a day in Europe/Paris, so this is shown as a local wall-clock time rather
     * than a countdown that would drift out of step with it.
     */
    fun resetLabel(): String? {
        if (resetAt <= 0) return null
        val formatter = java.text.SimpleDateFormat("EEEE HH:mm", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(resetAt))
    }

    companion object {
        /** Matches the server's own default; overwritten by the first `upload-quota` event. */
        const val DEFAULT_LIMIT = 100L * 1024 * 1024
    }
}
