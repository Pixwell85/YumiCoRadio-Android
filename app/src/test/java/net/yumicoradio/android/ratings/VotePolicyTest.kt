// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import org.junit.Assert.assertEquals
import org.junit.Test

class VotePolicyTest {
    @Test fun `tapping active choice removes vote`() {
        assertEquals(VoteChoice.NONE, nextVoteChoice(VoteChoice.LIKE, VoteChoice.LIKE))
        assertEquals(VoteChoice.NONE, nextVoteChoice(VoteChoice.DISLIKE, VoteChoice.DISLIKE))
    }

    @Test fun `opposite choice replaces vote`() {
        assertEquals(VoteChoice.DISLIKE, nextVoteChoice(VoteChoice.LIKE, VoteChoice.DISLIKE))
        assertEquals(VoteChoice.LIKE, nextVoteChoice(VoteChoice.DISLIKE, VoteChoice.LIKE))
    }
}
