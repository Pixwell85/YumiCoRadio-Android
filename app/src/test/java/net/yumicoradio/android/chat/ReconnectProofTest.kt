// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconnectProofTest {

    @Test
    fun `proof survives the same nickname but not an identity change`() {
        val proof = ReconnectProof()
        proof.accept("Zoe", "token-one")

        assertEquals("token-one", proof.forJoin("zoe"))
        assertNull(proof.forJoin("Other"))
        assertNull(proof.forJoin("Zoe"), "identity change did not erase the previous proof")
    }

    @Test
    fun `explicit clear removes the current proof`() {
        val proof = ReconnectProof()
        proof.accept("Other", "token-two")
        proof.clear()

        assertNull(proof.forJoin("Other"))
    }
}
