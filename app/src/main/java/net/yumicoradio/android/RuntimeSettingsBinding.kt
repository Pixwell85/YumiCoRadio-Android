// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps persisted settings applied for the whole process lifetime.
 *
 * These bindings deliberately belong to the application, not to a screen ViewModel: Android may
 * recreate playback or reconnect Live Chat after an app update before either screen is opened.
 */
internal fun bindRuntimeSettings(
    scope: CoroutineScope,
    eqEnabled: Flow<Boolean>,
    eqGains: Flow<List<Int>>,
    chatNickColor: Flow<String>,
    applyEqEnabled: (Boolean) -> Unit,
    applyEqGains: (List<Int>) -> Unit,
    applyChatNickColor: (String) -> Unit,
) {
    scope.launch { eqEnabled.collect(applyEqEnabled) }
    scope.launch { eqGains.collect(applyEqGains) }
    scope.launch { chatNickColor.collect(applyChatNickColor) }
}
