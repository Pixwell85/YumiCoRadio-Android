// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.json.JSONObject

internal enum class UploadAction { DISABLED, REQUIRE_ACCOUNT, PICK_FILE }

/** Missing or malformed permission is denied until the server explicitly grants it. */
internal fun parseUploadAccess(payload: JSONObject): Boolean = payload.optBoolean("allowed", false)

internal fun uploadAction(
    composerEnabled: Boolean,
    uploadsEnabled: Boolean,
    accessAllowed: Boolean,
    uploading: Boolean,
): UploadAction = when {
    !composerEnabled || !uploadsEnabled || uploading -> UploadAction.DISABLED
    !accessAllowed -> UploadAction.REQUIRE_ACCOUNT
    else -> UploadAction.PICK_FILE
}
