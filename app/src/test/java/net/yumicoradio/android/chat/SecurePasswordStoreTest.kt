// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurePasswordStoreTest {

    @Test
    fun `pack then unpack round-trips the iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ct = byteArrayOf(20, 21, 22, 23, 24)
        val (iv2, ct2) = SecurePasswordStore.unpack(SecurePasswordStore.pack(iv, ct))!!
        assertTrue(iv.contentEquals(iv2))
        assertTrue(ct.contentEquals(ct2))
    }

    @Test
    fun `unpack returns null on malformed input`() {
        assertNull(SecurePasswordStore.unpack("no-separator-here"))
        assertNull(SecurePasswordStore.unpack(""))
        assertNull(SecurePasswordStore.unpack("only:"))
    }
}
