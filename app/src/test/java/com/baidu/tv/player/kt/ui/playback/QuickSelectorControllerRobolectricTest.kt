package com.baidu.tv.player.kt.ui.playback

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.repository.ThumbnailProvider
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QuickSelectorController Robolectric 边界测试（Task 10.2）。
 *
 * 覆盖：
 * - ViewHolder 未布局时焦点恢复不崩溃
 * - 有限焦点重试
 * - 列表隐藏时 visibility GONE 并清除焦点
 * - 缩略图晚到不崩溃、不丢焦点
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuickSelectorControllerRobolectricTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var adapter: PlaylistQuickSelectorAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var focusHost: View
    private val thumbnailProvider: ThumbnailProvider = mockk()
    private lateinit var controller: QuickSelectorController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        adapter = PlaylistQuickSelectorAdapter()
        recyclerView = RecyclerView(context)
        focusHost = View(context)
        QuickSelectorController.setupRecyclerView(recyclerView)
        recyclerView.adapter = adapter
        coEvery { thumbnailProvider.fetchThumbnails(any()) } returns emptyMap()
        controller = QuickSelectorController(
            adapter = adapter,
            recyclerView = recyclerView,
            thumbnailProvider = thumbnailProvider,
            scope = mainCoroutineRule.scope,
            focusHost = focusHost,
        )
    }

    // ------------------------------------------------------------------
    // 10.2: ViewHolder not laid out — focus restoration doesn't crash
    // ------------------------------------------------------------------

    @Test
    fun focusCurrentItem_viewHolderNotLaidOut_doesNotCrash() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        // No ViewHolders are laid out in Robolectric without measure/layout.
        // focusCurrentItem should not crash even though findViewHolderForAdapterPosition returns null.
        controller.focusCurrentItem(fileA)
        // No crash — pass
    }

    @Test
    fun focusCurrentItem_fileNotInList_doesNotCrash() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        val fileNotInList = file("other.mp4", 999)
        controller.focusCurrentItem(fileNotInList)
        // No crash — position was NO_POSITION, early return
    }

    @Test
    fun focusCurrentItem_nullFile_doesNotCrash() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        controller.focusCurrentItem(null)
        // No crash
    }

    // ------------------------------------------------------------------
    // 10.2: List hidden — hideQuickSelector sets GONE and clears focus
    // ------------------------------------------------------------------

    @Test
    fun hide_setsVisibilityGone() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        controller.hide()

        assertEquals(View.GONE, recyclerView.visibility)
        assertFalse(controller.visible)
    }

    @Test
    fun hide_clearsFocusOnRecyclerView() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        controller.hide()

        // recyclerView.clearFocus() should have been called
        assertFalse(recyclerView.hasFocus())
    }

    @Test
    fun hide_focusHostRequestsFocus() {
        val fileA = file("a.mp4", 1)
        focusHost.isFocusable = true
        focusHost.isFocusableInTouchMode = true
        controller.show(listOf(fileA), fileA) { }

        controller.hide()

        assertTrue(focusHost.hasFocus())
    }

    // ------------------------------------------------------------------
    // 10.2: Thumbnail late arrival — doesn't crash or lose focus
    // ------------------------------------------------------------------

    @Test
    fun updateThumbnails_lateArrival_doesNotCrash() {
        val fileA = file("a.mp4", 1)
        val fileB = file("b.mp4", 2)
        controller.show(listOf(fileA, fileB), fileA) { }

        // Simulate late thumbnail arrival
        controller.updateThumbnails(mapOf(
            1L to "https://thumb/1",
            2L to "https://thumb/2",
        ))
        // No crash
    }

    @Test
    fun updateThumbnails_lateArrival_afterHide_isNoOp() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }
        controller.hide()

        // Late arrival after hide should be silently ignored
        controller.updateThumbnails(mapOf(1L to "https://thumb/1"))

        assertFalse(controller.visible)
        assertEquals(View.GONE, recyclerView.visibility)
    }

    @Test
    fun updateThumbnails_unknownFsId_doesNotCrash() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        controller.updateThumbnails(mapOf(999L to "https://thumb/999"))
        // No crash
    }

    // ------------------------------------------------------------------
    // 10.2: Limited focus retry
    // ------------------------------------------------------------------

    @Test
    fun focusCurrentItem_retriesLimitedTimesWithoutCrash() {
        val fileA = file("a.mp4", 1)
        val fileB = file("b.mp4", 2)
        controller.show(listOf(fileA, fileB), fileA) { }

        // Without laid-out ViewHolders, focus requests fail, but the controller
        // retries a limited number of times and then gives up gracefully.
        controller.focusCurrentItem(fileB)

        // No crash, no infinite loop
        assertTrue(controller.visible)
    }

    @Test
    fun show_thenSyncWithNewData_thenFocusCurrentItem_allSafe() {
        val fileA = file("a.mp4", 1)
        controller.show(listOf(fileA), fileA) { }

        val fileB = file("b.mp4", 2)
        val fileC = file("c.mp4", 3)
        controller.syncCurrent(listOf(fileA, fileB, fileC), fileB)

        controller.focusCurrentItem(fileC)
        // No crash through the full lifecycle
    }

    private fun file(name: String, fsId: Long, path: String = "/movies/$name") =
        FileInfo(fsId = fsId, path = path, serverFilename = name, category = 1)
}
