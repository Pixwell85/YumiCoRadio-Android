// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** Keeps the custom controls in step with Media3's native controller. */
class ChatVideoControlsState {
    private var controllerVisible = true
    private var activeInteractions = 0

    var visible: Boolean = true
        private set

    fun controllerVisibilityChanged(isVisible: Boolean) {
        controllerVisible = isVisible
        updateVisible()
    }

    fun interactionChanged(isActive: Boolean) {
        activeInteractions = if (isActive) {
            activeInteractions + 1
        } else {
            (activeInteractions - 1).coerceAtLeast(0)
        }
        updateVisible()
    }

    private fun updateVisible() {
        visible = controllerVisible || activeInteractions > 0
    }

    companion object {
        const val AUTO_HIDE_MILLIS = 2_000
    }
}
