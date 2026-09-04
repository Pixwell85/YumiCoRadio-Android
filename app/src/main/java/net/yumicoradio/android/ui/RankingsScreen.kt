// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import net.yumicoradio.android.R
import net.yumicoradio.android.ratings.MyVoteRow
import net.yumicoradio.android.ratings.MyVotesFilter
import net.yumicoradio.android.ratings.RankingPeriodType
import net.yumicoradio.android.ratings.RankingRow
import net.yumicoradio.android.ratings.RankingTab
import net.yumicoradio.android.ratings.VoteChoice
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.Win98ProgressBar
import net.yumicoradio.android.ui.components.sunken
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

@Composable
fun ColumnScope.RankingsContent(vm: RatingsViewModel, initialMyVotes: Boolean = false) {
    val state by vm.rankings.collectAsState()
    val account by vm.account.collectAsState()
    var selectedTrack by remember { mutableStateOf<TrackActionTarget?>(null) }

    LaunchedEffect(Unit) { if (initialMyVotes) vm.openMyVotes() else vm.openRankings() }
    LaunchedEffect(state.showingMyVotes, state.tab, state.type, state.anchor, state.myFilter) {
        while (true) {
            delay(30_000)
            if (state.showingMyVotes) vm.refreshMyVotes() else vm.refreshRankings()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Win98Button("Likes", Modifier.weight(1f), onClick = { vm.showRankingTab(RankingTab.LIKE) })
            Win98Button("Dislikes", Modifier.weight(1f), onClick = { vm.showRankingTab(RankingTab.DISLIKE) })
            Win98Button("My Votes", Modifier.weight(1f), enabled = canOpenMyVotes(account.signedIn), onClick = vm::openMyVotes)
        }
        Spacer(Modifier.height(8.dp))

        if (state.showingMyVotes) {
            MyVotesPane(vm, state, account.signedIn, onTrackSelected = { selectedTrack = it })
        } else {
            RankingsPane(vm, state, onTrackSelected = { selectedTrack = it })
        }
    }

    selectedTrack?.let { track ->
        TrackActionsDialog(
            track = track,
            icon = R.drawable.ic_win_rankings,
            onDismiss = { selectedTrack = null },
        )
    }

    state.message?.let { message ->
        Win98Dialog(
            title = "Track Rankings",
            icon = R.drawable.ic_win_rankings,
            onDismiss = vm::clearMessage,
            buttons = { Win98Button("OK", onClick = vm::clearMessage) },
        ) { RankingText(message) }
    }
}

@Composable
private fun ColumnScope.RankingsPane(
    vm: RatingsViewModel,
    state: RankingsUiState,
    onTrackSelected: (TrackActionTarget) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RankingPeriodType.entries.forEach { type ->
            Win98Button(type.wire.replaceFirstChar(Char::uppercase), Modifier.weight(1f), onClick = { vm.setPeriod(type) })
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Win98Button("<", onClick = vm::previousPeriod)
        Text(
            state.page?.period?.key ?: state.anchor,
            modifier = Modifier.weight(1f),
            fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Win98Button(">", enabled = state.page?.period?.canGoNext == true, onClick = vm::nextPeriod)
    }
    Spacer(Modifier.height(6.dp))
    if (state.loading && state.page == null) {
        Win98ProgressBar(0.65f)
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(end = 2.dp)) {
        val rows = state.page?.rows.orEmpty()
        if (!state.loading && rows.isEmpty()) Win98Fieldset("Results") { RankingText("No votes for this period yet.") }
        rows.forEach { row -> RankingEntry(row, onTrackSelected) }
    }
    Spacer(Modifier.height(6.dp))
    Pagination(
        page = state.page?.page ?: 1,
        pages = state.page?.totalPages ?: 0,
        previous = { vm.page(-1) },
        next = { vm.page(1) },
    )
}

@Composable
private fun ColumnScope.MyVotesPane(
    vm: RatingsViewModel,
    state: RankingsUiState,
    signedIn: Boolean,
    onTrackSelected: (TrackActionTarget) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MyVotesFilter.entries.forEach { filter ->
            Win98Button(filter.wire.replaceFirstChar(Char::uppercase), Modifier.weight(1f),
                onClick = { vm.setMyFilter(filter) })
        }
    }
    Spacer(Modifier.height(6.dp))
    RankingText(
        if (signedIn) "These votes are synchronized with your Yumi Co. Radio account."
        else "These votes are linked to this app. Sign in to synchronize them across devices.",
        dim = true,
    )
    Spacer(Modifier.height(6.dp))
    if (state.loading && state.myVotes == null) Win98ProgressBar(0.65f)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(end = 2.dp)) {
        val rows = state.myVotes?.rows.orEmpty()
        if (!state.loading && rows.isEmpty()) Win98Fieldset("My Votes") { RankingText("No saved votes.") }
        rows.forEach { row -> MyVoteEntry(row, onTrackSelected) }
    }
    Spacer(Modifier.height(6.dp))
    Pagination(
        page = state.myVotes?.page ?: 1,
        pages = state.myVotes?.totalPages ?: 0,
        previous = { vm.myVotesPage(-1) },
        next = { vm.myVotesPage(1) },
    )
}

@Composable
private fun RankingEntry(row: RankingRow, onTrackSelected: (TrackActionTarget) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 5.dp).background(Win98.Face).sunken()
            .tappable { onTrackSelected(row.trackActionTarget()) }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(row.track.artworkUrl, null, Modifier.size(44.dp).background(Win98.Sunken),
            placeholder = painterResource(R.drawable.default_cover), error = painterResource(R.drawable.default_cover))
        Spacer(Modifier.size(7.dp))
        Column(Modifier.weight(1f)) {
            RankingText("#${row.rank}  ${row.track.artist}")
            RankingText(row.track.title, dim = true)
        }
        RankingText("${row.count}${if (row.visitorHasMatchingVote) "  ✓" else ""}")
    }
}

@Composable
private fun MyVoteEntry(row: MyVoteRow, onTrackSelected: (TrackActionTarget) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 5.dp).background(Win98.Face).sunken()
            .tappable { onTrackSelected(row.trackActionTarget()) }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(row.track.artworkUrl, null, Modifier.size(44.dp).background(Win98.Sunken),
            placeholder = painterResource(R.drawable.default_cover), error = painterResource(R.drawable.default_cover))
        Spacer(Modifier.size(7.dp))
        Column(Modifier.weight(1f)) {
            RankingText(row.track.artist)
            RankingText(row.track.title, dim = true)
        }
        RankingText(if (row.latestChoice == VoteChoice.LIKE) "Like" else "Dislike")
    }
}

@Composable
private fun Pagination(page: Int, pages: Int, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Win98Button("<", enabled = page > 1, onClick = previous)
        Text("Page $page / ${maxOf(1, pages)}", Modifier.weight(1f), fontFamily = W95FA,
            fontSize = 11.sp, color = Win98.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Win98Button(">", enabled = page < pages, onClick = next)
    }
}

@Composable
private fun RankingText(text: String, dim: Boolean = false) {
    Text(text, fontFamily = W95FA, fontSize = 11.sp, lineHeight = 14.sp,
        color = if (dim) Win98.InkDim else Win98.Ink)
}
