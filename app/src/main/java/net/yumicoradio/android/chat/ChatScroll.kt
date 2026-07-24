// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * When a message list should keep following the conversation.
 *
 * Pulled out of the composable because the rule is easy to get subtly wrong and impossible to
 * check by eye: it decides between "the reader is at the bottom" and "the reader scrolled up to
 * read back", and the difference only shows once there is enough history to scroll.
 */
object ChatScroll {

    /**
     * How many items from the end still counts as being at the bottom.
     *
     * One item of slack absorbs the message that has just been appended but not yet scrolled to;
     * without it, every arrival would read as the reader having moved away.
     */
    const val SLACK = 2

    /**
     * Whether the end of the list is on screen.
     *
     * [lastVisibleIndex] is null when nothing is laid out yet — an empty list, or the first frame —
     * which counts as being at the bottom so the first messages are followed.
     */
    fun atBottom(lastVisibleIndex: Int?, totalItems: Int): Boolean =
        lastVisibleIndex == null || lastVisibleIndex >= totalItems - SLACK
}
