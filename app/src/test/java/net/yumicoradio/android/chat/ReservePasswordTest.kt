package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The client validates the chosen password before sending it, exactly as the website's dialog
 * does. The server re-checks length as a fallback, but a mismatch is caught only here — the two
 * fields never leave the device together.
 */
class ReservePasswordTest {

    @Test
    fun `accepts a matching password of at least eight characters`() {
        assertNull(ReservePassword.validate("longenough", "longenough"))
    }

    @Test
    fun `rejects a password shorter than eight`() {
        assertEquals(ReservePassword.Error.TOO_SHORT, ReservePassword.validate("short", "short"))
    }

    @Test
    fun `rejects when the two fields differ`() {
        assertEquals(
            ReservePassword.Error.MISMATCH,
            ReservePassword.validate("longenough", "longenoughX"),
        )
    }

    @Test
    fun `length is checked before the match, so a short mismatch reads as too short`() {
        assertEquals(ReservePassword.Error.TOO_SHORT, ReservePassword.validate("aaa", "bbb"))
    }

    @Test
    fun `accepts the maximum length and rejects one past it`() {
        assertNull(ReservePassword.validate("a".repeat(128), "a".repeat(128)))
        assertEquals(
            ReservePassword.Error.TOO_LONG,
            ReservePassword.validate("a".repeat(129), "a".repeat(129)),
        )
    }
}
