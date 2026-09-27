// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals

class UploadAccessPolicyTest {
    @Test
    fun `server upload access defaults closed and accepts explicit permission`() {
        assertEquals(false, parseUploadAccess(JSONObject()))
        assertEquals(false, parseUploadAccess(JSONObject().put("allowed", false)))
        assertEquals(true, parseUploadAccess(JSONObject().put("allowed", true)))
    }

    @Test
    fun `joined guest is sent to the account dialog instead of the picker`() {
        assertEquals(
            UploadAction.REQUIRE_ACCOUNT,
            uploadAction(
                composerEnabled = true,
                uploadsEnabled = true,
                accessAllowed = false,
                uploading = false,
            ),
        )
    }

    @Test
    fun `account holder can pick a file when uploads are available`() {
        assertEquals(
            UploadAction.PICK_FILE,
            uploadAction(
                composerEnabled = true,
                uploadsEnabled = true,
                accessAllowed = true,
                uploading = false,
            ),
        )
    }

    @Test
    fun `global upload shutdown and unavailable composer stay disabled`() {
        assertEquals(UploadAction.DISABLED, uploadAction(false, true, false, false))
        assertEquals(UploadAction.DISABLED, uploadAction(true, false, true, false))
        assertEquals(UploadAction.DISABLED, uploadAction(true, true, true, true))
    }
}
