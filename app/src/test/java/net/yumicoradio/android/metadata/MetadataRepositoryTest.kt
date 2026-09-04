// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.yumicoradio.android.metadata.model.NowPlaying
import org.junit.Test
import kotlin.test.assertTrue

class MetadataRepositoryTest {

    @Test fun `metadata keeps polling while audio is stopped`() = runBlocking {
        val fetches = AtomicInteger()
        val secondFetch = CompletableDeferred<Unit>()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = MetadataRepository(
            fetchSnapshot = {
                if (fetches.incrementAndGet() >= 2) secondFetch.complete(Unit)
                AzuraSnapshot(NowPlaying.EMPTY, emptyList())
            },
            scope = repositoryScope,
            io = Dispatchers.Default,
            pollMs = 10L,
        )

        repository.setPlaying(false)
        repository.start()
        try {
            withTimeout(1_000L) { secondFetch.await() }
            assertTrue(fetches.get() >= 2)
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }
}
