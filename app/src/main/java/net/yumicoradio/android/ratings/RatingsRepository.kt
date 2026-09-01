// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yumicoradio.android.account.AccountRepository
import java.util.TimeZone

class RatingsRepository(
    private val api: RatingsApi,
    private val voterTokens: SecureVoterTokenStore,
    private val accounts: AccountRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RatingsSnapshot())
    val state: StateFlow<RatingsSnapshot> = _state.asStateFlow()

    init {
        scope.launch {
            var previousAccount: String? = null
            accounts.state.filter { !it.restoring }.collect { account ->
                val currentAccount = account.session?.accountId
                if (currentAccount != null && previousAccount == null) mergeAnonymous()
                if (currentAccount == null && previousAccount != null) voterTokens.rotate()
                previousAccount = currentAccount
            }
        }
    }

    suspend fun refreshCurrent(): Result<CurrentVote> = operation {
        api.currentVote(timezone(), accounts.bearerToken(), voterTokens.loadOrCreate())
            .also { vote -> _state.update { it.copy(currentVote = vote) } }
    }

    suspend fun toggle(choice: VoteChoice): Result<CurrentVote> = operation {
        val current = _state.value.currentVote ?: api.currentVote(
            timezone(), accounts.bearerToken(), voterTokens.loadOrCreate(),
        )
        val target = nextVoteChoice(current.choice, choice)
        api.putVote(current, target, timezone(), accounts.bearerToken(), voterTokens.loadOrCreate())
            .also { vote -> _state.update { it.copy(currentVote = vote) } }
    }

    suspend fun rankings(tab: RankingTab, type: RankingPeriodType, anchor: String, page: Int): Result<RankingPage> =
        runCatching {
            api.rankings(tab, type, anchor, page, timezone(), accounts.bearerToken(), voterTokens.loadOrCreate())
        }

    suspend fun myVotes(filter: MyVotesFilter, page: Int): Result<MyVotesPage> {
        return runCatching {
            api.myVotes(filter, page, accounts.bearerToken(), voterTokens.loadOrCreate())
        }
    }

    suspend fun mergeAnonymous(): Result<Unit> {
        val bearer = accounts.bearerToken() ?: return Result.success(Unit)
        val voter = voterTokens.loadOrCreate()
        val result = runCatching { api.mergeAnonymous(bearer, voter) }
        val alreadyMerged = (result.exceptionOrNull() as? RatingsApiException)?.code == "anonymous_voter_merged"
        if (result.isSuccess || alreadyMerged) {
            voterTokens.rotate()
            runCatching { refreshCurrent() }
        }
        return if (alreadyMerged) Result.success(Unit) else result
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> {
        _state.update { it.copy(loading = true, message = null) }
        return runCatching { block() }
            .onFailure { error -> _state.update { it.copy(message = error.message ?: "Ratings request failed") } }
            .also { _state.update { snapshot -> snapshot.copy(loading = false) } }
    }

    private fun timezone(): String = TimeZone.getDefault().id.take(64)
}
