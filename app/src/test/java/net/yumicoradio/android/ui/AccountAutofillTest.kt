// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.ui.autofill.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAutofillTest {
    @Test
    fun `login identifier accepts username and email credentials`() {
        assertEquals(
            listOf(ContentType.Username, ContentType.EmailAddress),
            AccountAutofillType.LoginIdentifier.contentTypes,
        )
    }

    @Test
    fun `login password accepts stored password credentials`() {
        assertEquals(listOf(ContentType.Password), AccountAutofillType.LoginPassword.contentTypes)
    }
}
