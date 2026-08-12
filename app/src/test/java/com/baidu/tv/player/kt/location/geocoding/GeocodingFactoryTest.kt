package com.baidu.tv.player.kt.location.geocoding

import com.baidu.tv.player.kt.location.GpsCoordinate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [GeocodingFactory] 策略选择与回退测试（对应 tasks.md 7.9）。
 */
class GeocodingFactoryTest {

    private val coordinate = GpsCoordinate(39.9042, 116.4074) // 北京

    private class FakeStrategy(
        override val name: String,
        override val priority: Int,
        private val available: Boolean,
        private val result: String?,
        private val throws: Boolean = false,
    ) : GeocodingStrategy {
        var called = false
        override fun isAvailable(): Boolean = available
        override suspend fun getAddress(coordinate: GpsCoordinate): String? {
            called = true
            if (throws) throw IllegalStateException("boom")
            return result
        }
    }

    @Test
    fun ordersByPriorityAscending() {
        val factory = GeocodingFactory(
            setOf(
                FakeStrategy("C", priority = 3, available = true, result = null),
                FakeStrategy("A", priority = 1, available = true, result = null),
                FakeStrategy("B", priority = 2, available = true, result = null),
            ),
        )
        assertEquals(listOf("A", "B", "C"), factory.strategyOrder())
    }

    @Test
    fun returnsFirstAvailableNonNull() = runTest {
        val high = FakeStrategy("high", 1, available = true, result = "高优先级地址")
        val low = FakeStrategy("low", 2, available = true, result = "低优先级地址")
        val factory = GeocodingFactory(setOf(low, high))

        assertEquals("高优先级地址", factory.reverseGeocode(coordinate))
    }

    @Test
    fun returnsFirstParallelSuccessAndCancelsSlowerProbe() = runTest {
        val started = AtomicInteger(0)
        val allStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        fun markStarted() {
            if (started.incrementAndGet() == 2) allStarted.complete(Unit)
        }
        val slow = object : GeocodingStrategy {
            override val name = "slow"
            override val priority = 1
            override fun isAvailable() = true
            override suspend fun getAddress(coordinate: GpsCoordinate): String? {
                markStarted()
                try {
                    awaitCancellation()
                } finally {
                    slowCancelled.complete(Unit)
                }
            }
        }
        val fast = object : GeocodingStrategy {
            override val name = "fast"
            override val priority = 2
            override fun isAvailable() = true
            override suspend fun getAddress(coordinate: GpsCoordinate): String? {
                markStarted()
                allStarted.await()
                return "并行命中"
            }
        }

        assertEquals("并行命中", GeocodingFactory(setOf(slow, fast)).reverseGeocode(coordinate))
        assertEquals(Unit, slowCancelled.await())
    }

    @Test
    fun parentCancellationStopsAllProbes() = runTest {
        val started = AtomicInteger(0)
        val allStarted = CompletableDeferred<Unit>()
        val cancelled = AtomicInteger(0)
        val strategies = (1..2).map { priority ->
            object : GeocodingStrategy {
                override val name = "probe-$priority"
                override val priority = priority
                override fun isAvailable() = true
                override suspend fun getAddress(coordinate: GpsCoordinate): String? {
                    if (started.incrementAndGet() == 2) allStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.incrementAndGet()
                    }
                }
            }
        }.toSet()

        val job = launch { GeocodingFactory(strategies).reverseGeocode(coordinate) }
        allStarted.await()
        job.cancelAndJoin()

        assertEquals(2, cancelled.get())
    }

    @Test
    fun skipsUnavailableStrategies() = runTest {
        val unavailable = FakeStrategy("amap", 1, available = false, result = "不应使用")
        val available = FakeStrategy("nominatim", 3, available = true, result = "兜底地址")
        val factory = GeocodingFactory(setOf(unavailable, available))

        assertEquals("兜底地址", factory.reverseGeocode(coordinate))
        assertEquals(false, unavailable.called)
    }

    @Test
    fun fallsBackWhenHigherPriorityReturnsNull() = runTest {
        val first = FakeStrategy("first", 1, available = true, result = null)
        val second = FakeStrategy("second", 2, available = true, result = "回退命中")
        val factory = GeocodingFactory(setOf(first, second))

        assertEquals("回退命中", factory.reverseGeocode(coordinate))
        assertEquals(true, first.called)
    }

    @Test
    fun fallsBackWhenHigherPriorityThrows() = runTest {
        val throwing = FakeStrategy("throwing", 1, available = true, result = null, throws = true)
        val ok = FakeStrategy("ok", 2, available = true, result = "异常后回退")
        val factory = GeocodingFactory(setOf(throwing, ok))

        assertEquals("异常后回退", factory.reverseGeocode(coordinate))
    }

    @Test
    fun returnsNullWhenAllFail() = runTest {
        val factory = GeocodingFactory(
            setOf(
                FakeStrategy("a", 1, available = true, result = null),
                FakeStrategy("b", 2, available = false, result = "x"),
            ),
        )
        assertNull(factory.reverseGeocode(coordinate))
    }

    @Test
    fun returnsNullForInvalidCoordinate() = runTest {
        val strategy = FakeStrategy("a", 1, available = true, result = "不应调用")
        val factory = GeocodingFactory(setOf(strategy))
        assertNull(factory.reverseGeocode(GpsCoordinate(0.0, 0.0)))
        assertEquals(false, strategy.called)
    }
}
