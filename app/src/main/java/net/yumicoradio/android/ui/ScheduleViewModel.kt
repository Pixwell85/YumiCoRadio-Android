// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.schedule.QueueApi
import net.yumicoradio.android.schedule.ScheduleRepository

/** Owns the queue poll for as long as the schedule screen is on view. */
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScheduleRepository(
        queueApi = QueueApi((app as YumiApp).http),
        scope = viewModelScope,
    )

    val queue = repo.queue

    fun start() = repo.start()
    fun stop() = repo.stop()
}
