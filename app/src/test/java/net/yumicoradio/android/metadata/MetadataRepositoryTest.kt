// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.yumicoradio.android.metadata.model.NowPlaying
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataRepositoryTest {

    @Test fun `metadata does not keep polling while stopped in background`() = runBlocking {
        val fetches = AtomicInteger()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = MetadataRepository(
            fetchSnapshot = {
                fetches.incrementAndGet()
                AzuraSnapshot(NowPlaying.EMPTY, emptyList())
            },
            scope = repositoryScope,
            io = Dispatchers.Default,
            pollMs = 10L,
        )

        repository.setPlaying(false)
        repository.start()
        try {
            delay(100L)
            assertEquals(0, fetches.get())
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }

    @Test fun `metadata keeps polling while app is visible and audio is stopped`() = runBlocking {
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
        repository.setForeground(true)
        repository.start()
        try {
            withTimeout(1_000L) { secondFetch.await() }
            assertTrue(fetches.get() >= 2)
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }

    @Test fun `metadata keeps polling while audio plays in background`() = runBlocking {
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

        repository.setPlaying(true)
        repository.start()
        try {
            withTimeout(1_000L) { secondFetch.await() }
            assertTrue(fetches.get() >= 2)
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }

    @Test fun `starting after a foreground transition fetches only once`() = runBlocking {
        val fetches = AtomicInteger()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = MetadataRepository(
            fetchSnapshot = {
                fetches.incrementAndGet()
                AzuraSnapshot(NowPlaying.EMPTY, emptyList())
            },
            scope = repositoryScope,
            io = Dispatchers.Default,
            pollMs = 10_000L,
        )

        repository.setForeground(true)
        repository.start()
        try {
            withTimeout(1_000L) { while (fetches.get() == 0) delay(5L) }
            delay(50L)
            assertEquals(1, fetches.get())
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }

    @Test fun `metadata stops after app is hidden without playback and refreshes on return`() = runBlocking {
        val fetches = AtomicInteger()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = MetadataRepository(
            fetchSnapshot = {
                fetches.incrementAndGet()
                AzuraSnapshot(NowPlaying.EMPTY, emptyList())
            },
            scope = repositoryScope,
            io = Dispatchers.Default,
            pollMs = 10L,
        )

        repository.setPlaying(false)
        repository.setForeground(true)
        repository.start()
        try {
            withTimeout(1_000L) { while (fetches.get() < 2) delay(5L) }
            repository.setForeground(false)
            delay(30L) // Allow any in-flight request to finish before measuring idle traffic.
            val stoppedCount = fetches.get()
            delay(100L)
            assertEquals(stoppedCount, fetches.get())
            repository.setForeground(true)
            withTimeout(1_000L) { while (fetches.get() <= stoppedCount) delay(5L) }
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }

    @Test fun `metadata stops after playback ends in background and refreshes on resume`() = runBlocking {
        val fetches = AtomicInteger()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = MetadataRepository(
            fetchSnapshot = {
                fetches.incrementAndGet()
                AzuraSnapshot(NowPlaying.EMPTY, emptyList())
            },
            scope = repositoryScope,
            io = Dispatchers.Default,
            pollMs = 10L,
        )

        repository.setPlaying(true)
        repository.start()
        try {
            withTimeout(1_000L) { while (fetches.get() < 2) delay(5L) }
            repository.setPlaying(false)
            delay(30L) // Allow any in-flight request to finish before measuring idle traffic.
            val stoppedCount = fetches.get()
            delay(100L)
            assertEquals(stoppedCount, fetches.get())
            repository.setPlaying(true)
            withTimeout(1_000L) { while (fetches.get() <= stoppedCount) delay(5L) }
        } finally {
            repository.stop()
            repositoryScope.cancel()
        }
    }
}
