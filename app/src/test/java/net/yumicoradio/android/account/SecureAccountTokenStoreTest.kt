// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureAccountTokenStoreTest {
    @Test fun `encrypted blob envelope round trips`() {
        val iv = ByteArray(12) { it.toByte() }
        val encrypted = ByteArray(48) { (it * 3).toByte() }
        val unpacked = SecureAccountTokenStore.unpack(SecureAccountTokenStore.pack(iv, encrypted))!!
        assertArrayEquals(iv, unpacked.first)
        assertArrayEquals(encrypted, unpacked.second)
    }

    @Test fun `malformed or unknown envelope is rejected`() {
        assertNull(SecureAccountTokenStore.unpack("v2:00:00"))
        assertNull(SecureAccountTokenStore.unpack("v1:not-hex:00"))
    }
}
