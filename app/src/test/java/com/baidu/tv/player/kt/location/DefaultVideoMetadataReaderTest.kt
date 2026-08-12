package com.baidu.tv.player.kt.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DefaultVideoMetadataReaderTest {

    @Test
    fun invalidNonEmptyPlatformValueDoesNotShortCircuitQuickTime() = runTest {
        val result = DefaultVideoMetadataReader.raceValidLocations(
            platformProbe = { "not-a-valid-location" },
            quickTimeProbe = { "+39.1744+117.2056+004.822/" },
        )

        assertEquals("+39.1744+117.2056+004.822/", result)
    }

    @Test
    fun probesPlatformAndQuickTimeInParallelAndCancelsSlowerOne() = runTest {
        val started = AtomicInteger(0)
        val bothStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        fun markStarted() {
            if (started.incrementAndGet() == 2) bothStarted.complete(Unit)
        }

        val result = DefaultVideoMetadataReader.raceValidLocations(
            platformProbe = {
                markStarted()
                try {
                    awaitCancellation()
                } finally {
                    slowCancelled.complete(Unit)
                }
            },
            quickTimeProbe = {
                markStarted()
                bothStarted.await()
                "+39.1744+117.2056+004.822/"
            },
        )

        assertEquals("+39.1744+117.2056+004.822/", result)
        assertEquals(Unit, slowCancelled.await())
    }
}
