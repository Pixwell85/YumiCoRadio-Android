// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAdminPolicyTest {
    @Test fun onlyAdministratorsCanSeeAccountAdministration() {
        assertTrue(canManageAccounts("admin"))
        assertFalse(canManageAccounts("user"))
        assertFalse(canManageAccounts(null))
    }
}
