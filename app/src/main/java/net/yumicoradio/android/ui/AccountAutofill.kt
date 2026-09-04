// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.ui.autofill.ContentType

internal enum class AccountAutofillType(val contentTypes: List<ContentType>) {
    LoginIdentifier(listOf(ContentType.Username, ContentType.EmailAddress)),
    LoginPassword(listOf(ContentType.Password)),
    ;

    val contentType: ContentType
        get() = contentTypes.reduce { combined, type -> combined + type }
}
