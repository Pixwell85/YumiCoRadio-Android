// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.ratings.MyVotesFilter
import net.yumicoradio.android.ratings.MyVotesPage
import net.yumicoradio.android.ratings.RankingPage
import net.yumicoradio.android.ratings.RankingPeriodType
import net.yumicoradio.android.ratings.RankingTab
import net.yumicoradio.android.ratings.RatingsSnapshot
import net.yumicoradio.android.ratings.VoteChoice
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class RankingsUiState(
    val tab: RankingTab = RankingTab.LIKE,
    val type: RankingPeriodType = RankingPeriodType.DAY,
    val anchor: String = today(),
    val page: RankingPage? = null,
    val myVotes: MyVotesPage? = null,
    val myFilter: MyVotesFilter = MyVotesFilter.ALL,
    val showingMyVotes: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
)

class RatingsViewModel(application: Application) : AndroidViewModel(application) {
    private val yumi = application as YumiApp
    private val repository = yumi.ratings
    val vote: StateFlow<RatingsSnapshot> = repository.state
    val account = yumi.account.state

    private val _rankings = MutableStateFlow(RankingsUiState())
    val rankings: StateFlow<RankingsUiState> = _rankings.asStateFlow()

    fun refreshVote() { viewModelScope.launch { repository.refreshCurrent() } }
    fun toggle(choice: VoteChoice) { viewModelScope.launch { repository.toggle(choice) } }
    fun clearVoteMessage() = repository.clearMessage()

    fun openRankings() {
        _rankings.value = _rankings.value.copy(showingMyVotes = false)
        refreshRankings()
    }

    fun openMyVotes() {
        _rankings.value = _rankings.value.copy(showingMyVotes = true)
        refreshMyVotes()
    }

    fun setTab(tab: RankingTab) {
        _rankings.value = _rankings.value.copy(tab = tab, page = null)
        refreshRankings()
    }

    fun setPeriod(type: RankingPeriodType) {
        _rankings.value = _rankings.value.copy(type = type, anchor = currentAnchor(type), page = null)
        refreshRankings()
    }

    fun previousPeriod() { shiftPeriod(-1) }
    fun nextPeriod() { if (_rankings.value.page?.period?.canGoNext == true) shiftPeriod(1) }

    fun page(delta: Int) {
        val state = _rankings.value
        val current = state.page?.page ?: 1
        val target = (current + delta).coerceIn(1, maxOf(1, state.page?.totalPages ?: 1))
        loadRankings(target)
    }

    fun setMyFilter(filter: MyVotesFilter) {
        _rankings.value = _rankings.value.copy(myFilter = filter, myVotes = null)
        loadMyVotes(1)
    }

    fun myVotesPage(delta: Int) {
        val state = _rankings.value
        val current = state.myVotes?.page ?: 1
        val target = (current + delta).coerceIn(1, maxOf(1, state.myVotes?.totalPages ?: 1))
        loadMyVotes(target)
    }

    fun refreshRankings() = loadRankings(_rankings.value.page?.page ?: 1)
    fun refreshMyVotes() = loadMyVotes(_rankings.value.myVotes?.page ?: 1)
    fun clearMessage() { _rankings.value = _rankings.value.copy(message = null) }

    private fun loadRankings(page: Int) {
        val selected = _rankings.value
        viewModelScope.launch {
            _rankings.value = _rankings.value.copy(loading = true, message = null)
            repository.rankings(selected.tab, selected.type, selected.anchor, page)
                .onSuccess { result ->
                    _rankings.value = _rankings.value.copy(
                        page = result, anchor = result.period.key, loading = false,
                    )
                }
                .onFailure { error -> _rankings.value = _rankings.value.copy(loading = false, message = error.message) }
        }
    }

    private fun loadMyVotes(page: Int) {
        val selected = _rankings.value
        viewModelScope.launch {
            _rankings.value = _rankings.value.copy(loading = true, message = null)
            repository.myVotes(selected.myFilter, page)
                .onSuccess { result -> _rankings.value = _rankings.value.copy(myVotes = result, loading = false) }
                .onFailure { error -> _rankings.value = _rankings.value.copy(loading = false, message = error.message) }
        }
    }

    private fun shiftPeriod(amount: Int) {
        val state = _rankings.value
        val calendar = Calendar.getInstance().apply {
            time = formatter(state.type).parse(state.anchor) ?: DateFallback
            add(when (state.type) {
                RankingPeriodType.DAY -> Calendar.DAY_OF_MONTH
                RankingPeriodType.WEEK -> Calendar.WEEK_OF_YEAR
                RankingPeriodType.MONTH -> Calendar.MONTH
            }, amount)
        }
        _rankings.value = state.copy(anchor = formatter(state.type).format(calendar.time), page = null)
        loadRankings(1)
    }
}

private val DateFallback get() = Calendar.getInstance().time
private fun formatter(type: RankingPeriodType) =
    SimpleDateFormat(if (type == RankingPeriodType.MONTH) "yyyy-MM" else "yyyy-MM-dd", Locale.ROOT)
private fun currentAnchor(type: RankingPeriodType) = formatter(type).format(Calendar.getInstance().time)
private fun today() = currentAnchor(RankingPeriodType.DAY)
