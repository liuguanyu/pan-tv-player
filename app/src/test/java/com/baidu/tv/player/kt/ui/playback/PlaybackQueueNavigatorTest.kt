package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueNavigatorTest {

    // ==================================================================
    // 8.1 顺序、倒序、单曲模式参数化测试
    // ==================================================================

    // ---------- 顺序模式 ----------

    @Test
    fun sequential_middleNext_advancesByOne() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=2 → next=3
        assertEquals(3, nav.nextIndex(5, 2, PlayMode.SEQUENTIAL, forward = true))
    }

    @Test
    fun sequential_middlePrev_goesBackByOne() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=2 → prev=1
        assertEquals(1, nav.nextIndex(5, 2, PlayMode.SEQUENTIAL, forward = false))
    }

    @Test
    fun sequential_firstNextForward_wrapsToLast() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=0, forward=false → 4 (wrap)
        assertEquals(4, nav.nextIndex(5, 0, PlayMode.SEQUENTIAL, forward = false))
    }

    @Test
    fun sequential_lastNextForward_wrapsToFirst() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=4, forward=true → 0 (wrap)
        assertEquals(0, nav.nextIndex(5, 4, PlayMode.SEQUENTIAL, forward = true))
    }

    @Test
    fun sequential_previousIndex_matchesForwardFalse() {
        val nav = PlaybackQueueNavigator()
        assertEquals(
            nav.nextIndex(5, 2, PlayMode.SEQUENTIAL, forward = false),
            nav.previousIndex(5, 2, PlayMode.SEQUENTIAL),
        )
    }

    // ---------- 倒序模式 ----------

    @Test
    fun reverse_middleNext_goesBackByOne() {
        val nav = PlaybackQueueNavigator()
        // reverse: forward is opposite of sequential
        // size=5, current=2, forward=true → 1
        assertEquals(1, nav.nextIndex(5, 2, PlayMode.REVERSE, forward = true))
    }

    @Test
    fun reverse_middlePrev_advancesByOne() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=2, forward=false → 3
        assertEquals(3, nav.nextIndex(5, 2, PlayMode.REVERSE, forward = false))
    }

    @Test
    fun reverse_firstNextForward_wrapsToLast() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=0, forward=true → (0-1+5)%5 = 4
        assertEquals(4, nav.nextIndex(5, 0, PlayMode.REVERSE, forward = true))
    }

    @Test
    fun reverse_lastNextForward_goesToSecondLast() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=4, forward=true → 3
        assertEquals(3, nav.nextIndex(5, 4, PlayMode.REVERSE, forward = true))
    }

    @Test
    fun reverse_lastPrevForward_wrapsToFirst() {
        val nav = PlaybackQueueNavigator()
        // size=5, current=4, forward=false → (4+1)%5 = 0
        assertEquals(0, nav.nextIndex(5, 4, PlayMode.REVERSE, forward = false))
    }

    // ---------- 单曲模式 ----------

    @Test
    fun single_anyIndex_returnsSameIndex() {
        val nav = PlaybackQueueNavigator()
        assertEquals(0, nav.nextIndex(5, 0, PlayMode.SINGLE, forward = true))
        assertEquals(2, nav.nextIndex(5, 2, PlayMode.SINGLE, forward = true))
        assertEquals(4, nav.nextIndex(5, 4, PlayMode.SINGLE, forward = true))
    }

    @Test
    fun single_anyIndex_previousReturnsSameIndex() {
        val nav = PlaybackQueueNavigator()
        assertEquals(3, nav.previousIndex(5, 3, PlayMode.SINGLE))
    }

    // ==================================================================
    // 8.2 随机模式 - 固定随机源
    // ==================================================================

    @Test
    fun random_singleElement_returnsZero() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        assertEquals(0, nav.nextIndex(1, 0, PlayMode.RANDOM, forward = true))
    }

    @Test
    fun random_doesNotImmediatelyReturnCurrentIndex() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        val size = 5
        val currentIndex = 2
        val next = nav.nextIndex(size, currentIndex, PlayMode.RANDOM, forward = true)
        assertNotNull(next)
        assertTrue(
            "random next should not equal current index on first call (queue excludes current)",
            next != currentIndex,
        )
    }

    @Test
    fun random_fullRound_visitsAllIndicesWithoutRepetition() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        val size = 5
        val startIndex = 0
        val visited = mutableListOf<Int>()
        var current = startIndex
        // A fresh queue contains size-1 candidates (all except current).
        // After size-1 calls, all visited indices should be unique and none equal startIndex.
        repeat(size - 1) {
            val next = nav.nextIndex(size, current, PlayMode.RANDOM, forward = true)!!
            visited.add(next)
            current = next
        }
        assertEquals(size - 1, visited.size)
        assertEquals(
            "all ${size - 1} indices should be unique within one queue fill",
            size - 1, visited.toSet().size,
        )
        assertTrue("no index should equal the start index within one fill", visited.none { it == startIndex })
    }

    @Test
    fun random_queueRefillsAfterExhaustion() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        val size = 3
        // First round: queue has size-1 = 2 candidates
        val firstRound = (0 until size - 1).map {
            nav.nextIndex(size, 0, PlayMode.RANDOM, forward = true)!!
        }
        assertEquals(size - 1, firstRound.toSet().size)

        // Clear and start a new round — should produce new indices
        nav.clearRandomQueue()
        val secondRound = (0 until size - 1).map {
            nav.nextIndex(size, 0, PlayMode.RANDOM, forward = true)!!
        }
        assertEquals(size - 1, secondRound.toSet().size)
    }

    @Test
    fun random_clearQueue_producesFreshSequence() {
        val nav = PlaybackQueueNavigator(random = Random(123))
        val size = 4

        val firstCall = nav.nextIndex(size, 0, PlayMode.RANDOM, forward = true)
        nav.clearRandomQueue()
        val afterClear = nav.nextIndex(size, 0, PlayMode.RANDOM, forward = true)

        assertNotNull(firstCall)
        assertNotNull(afterClear)
        // After clear, the queue is rebuilt excluding current (0), so result != 0
        assertTrue(afterClear != 0)
    }

    // ==================================================================
    // 8.3 边界测试
    // ==================================================================

    @Test
    fun emptyList_returnsNull() {
        val nav = PlaybackQueueNavigator()
        assertNull(nav.nextIndex(0, 0, PlayMode.SEQUENTIAL, forward = true))
        assertNull(nav.nextIndex(0, 0, PlayMode.RANDOM, forward = true))
        assertNull(nav.nextIndex(0, 0, PlayMode.REVERSE, forward = true))
        assertNull(nav.nextIndex(0, 0, PlayMode.SINGLE, forward = true))
    }

    @Test
    fun singleElementList_returnsZero() {
        val nav = PlaybackQueueNavigator()
        assertEquals(0, nav.nextIndex(1, 0, PlayMode.SEQUENTIAL, forward = true))
        assertEquals(0, nav.nextIndex(1, 0, PlayMode.REVERSE, forward = true))
        assertEquals(0, nav.nextIndex(1, 0, PlayMode.SINGLE, forward = true))
        assertEquals(0, nav.nextIndex(1, 0, PlayMode.RANDOM, forward = true))
    }

    @Test
    fun indexOutOfBounds_sequential_wrapsViaModulo() {
        val nav = PlaybackQueueNavigator()
        // currentIndex=10, size=5 → (10+1)%5 = 1
        assertEquals(1, nav.nextIndex(5, 10, PlayMode.SEQUENTIAL, forward = true))
        // currentIndex=-1 treated as -1 → (-1+1+5)%5 = 0... wait: (-1-1+5)%5 = 3
        // Actually for forward=false: (currentIndex - 1 + size) % size = (-1-1+5)%5 = 3
        assertEquals(3, nav.nextIndex(5, -1, PlayMode.SEQUENTIAL, forward = false))
    }

    @Test
    fun listChanges_navigatorDoesNotCrash() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        // Start with size=5
        val first = nav.nextIndex(5, 0, PlayMode.RANDOM, forward = true)
        assertNotNull(first)
        // List shrinks to size=2 — clear queue first (as the ViewModel would on list change)
        nav.clearRandomQueue()
        val second = nav.nextIndex(2, 0, PlayMode.RANDOM, forward = true)
        assertNotNull(second)
        assertTrue("fresh queue index should be in bounds", second in 0 until 2)
        // List grows to size=10
        nav.clearRandomQueue()
        val third = nav.nextIndex(10, 0, PlayMode.RANDOM, forward = true)
        assertNotNull(third)
        assertTrue("fresh queue index should be in bounds", third in 0 until 10)
    }

    @Test
    fun navigator_doesNotModifyInputList() {
        val nav = PlaybackQueueNavigator()
        val originalList = listOf("a", "b", "c", "d", "e")
        val snapshot = originalList.toList()

        // Call nextIndex multiple times — the navigator only takes size, not the list
        nav.nextIndex(originalList.size, 0, PlayMode.SEQUENTIAL, forward = true)
        nav.nextIndex(originalList.size, 0, PlayMode.RANDOM, forward = true)
        nav.clearRandomQueue()

        assertEquals(snapshot, originalList)
    }

    // ==================================================================
    // initialIndex 测试
    // ==================================================================

    @Test
    fun initialIndex_random_returnsValidIndex() {
        val nav = PlaybackQueueNavigator(random = Random(42))
        val size = 5
        val initial = nav.initialIndex(size, 2, PlayMode.RANDOM)
        assertTrue(initial in 0 until size)
    }

    @Test
    fun initialIndex_reverse_returnsLastIndex() {
        val nav = PlaybackQueueNavigator()
        assertEquals(4, nav.initialIndex(5, 2, PlayMode.REVERSE))
    }

    @Test
    fun initialIndex_sequential_returnsPreferredClamped() {
        val nav = PlaybackQueueNavigator()
        assertEquals(2, nav.initialIndex(5, 2, PlayMode.SEQUENTIAL))
        assertEquals(4, nav.initialIndex(5, 10, PlayMode.SEQUENTIAL))
        assertEquals(0, nav.initialIndex(5, -1, PlayMode.SEQUENTIAL))
    }

    @Test
    fun initialIndex_singleElement_returnsZero() {
        val nav = PlaybackQueueNavigator()
        assertEquals(0, nav.initialIndex(1, 0, PlayMode.SEQUENTIAL))
        assertEquals(0, nav.initialIndex(1, 5, PlayMode.RANDOM))
    }

    @Test
    fun initialIndex_emptyList_returnsClampedPreferred() {
        val nav = PlaybackQueueNavigator()
        assertEquals(0, nav.initialIndex(0, 0, PlayMode.SEQUENTIAL))
    }
}
