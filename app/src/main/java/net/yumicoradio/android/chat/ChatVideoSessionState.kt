// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

data class ChatVideoTarget(val key: String, val url: String)

/** Pure ownership state for the one chat video allowed to be active at a time. */
class ChatVideoSessionState {
    var activeTarget: ChatVideoTarget? = null
        private set

    val activeUrl: String? get() = activeTarget?.url

    var shouldResumeRadio: Boolean = false
        private set

    var fullscreenKey: String? = null
        private set

    private var activeTargetVisible = true

    fun start(url: String, radioWasPlaying: Boolean) {
        start(ChatVideoTarget(key = url, url = url), radioWasPlaying)
    }

    fun start(target: ChatVideoTarget, radioWasPlaying: Boolean) {
        activeTarget = target
        shouldResumeRadio = radioWasPlaying
        fullscreenKey = null
        activeTargetVisible = true
    }

    /** Switches the active media without losing the radio intent captured by the first video. */
    fun switchTo(url: String) {
        switchTo(ChatVideoTarget(key = url, url = url))
    }

    fun switchTo(target: ChatVideoTarget) {
        activeTarget = target
        fullscreenKey = null
        activeTargetVisible = true
    }

    /** Returns whether the radio must resume. A stale message key leaves the session untouched. */
    fun finish(key: String): Boolean {
        if (activeTarget?.key != key) return false
        val resume = shouldResumeRadio
        activeTarget = null
        shouldResumeRadio = false
        fullscreenKey = null
        activeTargetVisible = true
        return resume
    }

    fun enterFullscreen(key: String): Boolean {
        if (activeTarget?.key != key) return false
        fullscreenKey = key
        return true
    }

    /** Returns true when the fullscreen source disappeared and playback should now be released. */
    fun exitFullscreen(): Boolean {
        val key = fullscreenKey ?: return false
        fullscreenKey = null
        return activeTarget?.key == key && !activeTargetVisible
    }

    /** Stores source visibility even in fullscreen, where an offscreen video must keep playing. */
    fun updateVisibility(key: String, visible: Boolean): Boolean {
        if (activeTarget?.key != key) return false
        activeTargetVisible = visible
        return !visible && fullscreenKey != key
    }

    fun shouldReleaseWhenHidden(key: String): Boolean =
        activeTarget?.key == key && fullscreenKey != key

    /**
     * Transfers playback out of the app without resuming the radio underneath the external player.
     * The return value carries the original radio intent so the UI can restore it on return.
     */
    fun handoffToExternal(key: String): Boolean {
        if (activeTarget?.key != key) return false
        val resumeOnReturn = shouldResumeRadio
        activeTarget = null
        shouldResumeRadio = false
        fullscreenKey = null
        activeTargetVisible = true
        return resumeOnReturn
    }
}
