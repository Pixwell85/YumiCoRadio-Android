// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.metadata.AzuraNowPlayingApi
import net.yumicoradio.android.schedule.QueueApi
import net.yumicoradio.android.schedule.ScheduleRepository

/** Owns the live timeline poll for as long as the schedule screen is on view. */
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val yumi = app as YumiApp
    private val queueApi = QueueApi(yumi.http)
    private val nowPlayingApi = AzuraNowPlayingApi(yumi.http)
    private val repo = ScheduleRepository(
        fetchQueue = queueApi::fetch,
        fetchSnapshot = nowPlayingApi::fetch,
        scope = viewModelScope,
    )

    val timeline = repo.timeline

    fun start() = repo.start()
    fun stop() = repo.stop()
}
