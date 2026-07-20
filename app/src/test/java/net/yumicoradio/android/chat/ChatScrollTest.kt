package net.yumicoradio.android.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The follow rule for the message list.
 *
 * The bug this covers: the chat stopped scrolling to the newest line as soon as the keyboard
 * appeared. Two things were wrong, and only one of them lives here — the other is that nothing
 * re-ran the scroll when the viewport resized, which is wiring rather than arithmetic.
 *
 * What this pins is the predicate itself, and in particular that it stays true across the frame
 * where a message has been appended but not yet scrolled to. Without that slack every arrival
 * would look like the reader having scrolled away, and the list would follow nothing at all.
 */
class ChatScrollTest {

    @Test
    fun `an empty or unlaid-out list counts as being at the bottom`() {
        assertTrue(ChatScroll.atBottom(lastVisibleIndex = null, totalItems = 0))
        assertTrue(ChatScroll.atBottom(lastVisibleIndex = null, totalItems = 12))
    }

    @Test
    fun `seeing the last item is being at the bottom`() {
        assertTrue(ChatScroll.atBottom(lastVisibleIndex = 9, totalItems = 10))
    }

    /**
     * The frame that matters. A message has just been appended, so the count is already 11 while
     * the layout still ends at index 9. This has to read as "at the bottom" or the list never
     * follows the conversation at all.
     */
    @Test
    fun `a just-appended message still counts as being at the bottom`() {
        assertTrue(ChatScroll.atBottom(lastVisibleIndex = 9, totalItems = 11))
    }

    @Test
    fun `scrolling back up is not being at the bottom`() {
        assertFalse(ChatScroll.atBottom(lastVisibleIndex = 4, totalItems = 40))
        // One item beyond the slack is enough to count as having moved away.
        assertFalse(ChatScroll.atBottom(lastVisibleIndex = 7, totalItems = 10))
    }

    /** The boundary, stated explicitly so a change to SLACK has to be deliberate. */
    @Test
    fun `the slack is exactly one item beyond the last`() {
        assertTrue(ChatScroll.atBottom(lastVisibleIndex = 8, totalItems = 10))
        assertFalse(ChatScroll.atBottom(lastVisibleIndex = 7, totalItems = 10))
    }
}
