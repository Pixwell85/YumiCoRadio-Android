// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** Keeps the radio-resume intent while Android temporarily recreates the Chat UI. */
class ChatVideoExternalHandoffState {
    private var resumeRadioOnReturn = false

    fun defer(shouldResumeRadio: Boolean) {
        resumeRadioOnReturn = resumeRadioOnReturn || shouldResumeRadio
    }

    fun consumeResumeIntent(): Boolean {
        val resume = resumeRadioOnReturn
        resumeRadioOnReturn = false
        return resume
    }
}
