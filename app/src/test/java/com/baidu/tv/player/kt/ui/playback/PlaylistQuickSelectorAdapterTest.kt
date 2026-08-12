package com.baidu.tv.player.kt.ui.playback

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.model.FileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 快速选播 Adapter 表征测试（Task 1.2）。
 *
 * 覆盖 selectAt、非法位置、stable ID、当前项高亮和缩略图局部更新。
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistQuickSelectorAdapterTest {

    @Test
    fun selectAt_validPosition_returnsTrueAndInvokesCallback() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = file("a.mp4", fsId = 1)
        val selected = mutableListOf<FileInfo>()
        adapter.configure(listOf(file), null) { selected.add(it) }

        assertTrue(adapter.selectAt(0))
        assertEquals(listOf(file), selected)
    }

    @Test
    fun selectAt_invalidPosition_returnsFalse() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = file("a.mp4", fsId = 1)
        adapter.configure(listOf(file), null) { }

        assertFalse(adapter.selectAt(1))
        assertFalse(adapter.selectAt(-1))
    }

    @Test
    fun selectAt_emptyItems_returnsFalse() {
        val adapter = PlaylistQuickSelectorAdapter()
        adapter.configure(emptyList(), null) { }

        assertFalse(adapter.selectAt(0))
    }

    @Test
    fun getItemId_usesFsIdWhenNonZero() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        val fileB = file("b.mp4", fsId = 2)
        adapter.configure(listOf(fileA, fileB), null) { }

        assertEquals(1L, adapter.getItemId(0))
        assertEquals(2L, adapter.getItemId(1))
    }

    @Test
    fun getItemId_stableAcrossReconfigure() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        val fileB = file("b.mp4", fsId = 2)

        adapter.configure(listOf(fileA), null) { }
        val idBefore = adapter.getItemId(0)

        adapter.configure(listOf(fileA, fileB), null) { }
        val idAfter = adapter.getItemId(0)

        assertEquals(idBefore, idAfter)
    }

    @Test
    fun getItemId_usesPathHashWhenFsIdIsZero() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 0)
        val fileB = file("b.mp4", fsId = 0)
        adapter.configure(listOf(fileA, fileB), null) { }

        val idA = adapter.getItemId(0)
        val idB = adapter.getItemId(1)
        // 不同路径应产生不同稳定 ID
        assertFalse(idA == idB)
        // 同一文件重配后 ID 不变
        adapter.configure(listOf(fileA, fileB), null) { }
        assertEquals(idA, adapter.getItemId(0))
        assertEquals(idB, adapter.getItemId(1))
    }

    @Test
    fun positionOf_byFsId() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        val fileB = file("b.mp4", fsId = 2)
        adapter.configure(listOf(fileA, fileB), null) { }

        assertEquals(0, adapter.positionOf(fileA))
        assertEquals(1, adapter.positionOf(fileB))
    }

    @Test
    fun positionOf_byPathWhenFsIdIsZero() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 0)
        val fileB = file("b.mp4", fsId = 0)
        adapter.configure(listOf(fileA, fileB), null) { }

        assertEquals(0, adapter.positionOf(fileA))
        assertEquals(1, adapter.positionOf(fileB))
    }

    @Test
    fun positionOf_nullFileReturnsNoPosition() {
        val adapter = PlaylistQuickSelectorAdapter()
        adapter.configure(emptyList(), null) { }

        assertEquals(RecyclerView.NO_POSITION, adapter.positionOf(null))
    }

    @Test
    fun positionOf_fileNotInListReturnsNoPosition() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        adapter.configure(listOf(fileA), null) { }

        val fileB = file("b.mp4", fsId = 2)
        assertEquals(RecyclerView.NO_POSITION, adapter.positionOf(fileB))
    }

    @Test
    fun setHighlightedFile_updatesFocusBarVisibilityOnRebind() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        val fileB = file("b.mp4", fsId = 2)
        adapter.configure(listOf(fileA, fileB), fileA) { }

        val holder0 = createHolder()
        val holder1 = createHolder()
        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        // fileA is highlighted → holder0 focusBar VISIBLE, holder1 GONE
        assertEquals(View.VISIBLE, holder0.focusBar.visibility)
        assertEquals(View.GONE, holder1.focusBar.visibility)

        // Switch highlight to fileB
        adapter.setHighlightedFile(fileB)
        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        assertEquals(View.GONE, holder0.focusBar.visibility)
        assertEquals(View.VISIBLE, holder1.focusBar.visibility)
    }

    @Test
    fun setHighlightedFile_sameFileIsNoOp() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        adapter.configure(listOf(fileA), fileA) { }

        // Should not crash or change state
        adapter.setHighlightedFile(fileA)

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, holder.focusBar.visibility)
    }

    @Test
    fun setHighlightedFile_toNullHidesAllBars() {
        val adapter = PlaylistQuickSelectorAdapter()
        val fileA = file("a.mp4", fsId = 1)
        adapter.configure(listOf(fileA), fileA) { }

        adapter.setHighlightedFile(null)
        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.focusBar.visibility)
    }

    @Test
    fun onBindViewHolder_displaysFileName() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = FileInfo(
            fsId = 1, path = "/movies/a.mp4", serverFilename = "a.mp4", category = 1,
        )
        adapter.configure(listOf(file), null) { }

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals("a.mp4", holder.fileName.text.toString())
    }

    @Test
    fun onBindViewHolder_fallsBackToPathWhenNoServerFilename() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = FileInfo(
            fsId = 1, path = "/movies/no-name.mp4", serverFilename = null, category = 1,
        )
        adapter.configure(listOf(file), null) { }

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals("no-name.mp4", holder.fileName.text.toString())
    }

    @Test
    fun updateThumbnails_doesNotCrashOnValidFsId() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = file("a.mp4", fsId = 1)
        adapter.configure(listOf(file), null) { }

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        // Should not crash; without a RecyclerView, notifyItemChanged is a no-op,
        // but the thumbnail cache is updated internally.
        adapter.updateThumbnails(mapOf(1L to "https://thumb/1"))
    }

    @Test
    fun updateThumbnails_ignoresUnknownFsId() {
        val adapter = PlaylistQuickSelectorAdapter()
        val file = file("a.mp4", fsId = 1)
        adapter.configure(listOf(file), null) { }

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        // Unknown fsId should not crash or affect binding
        adapter.updateThumbnails(mapOf(999L to "https://thumb/999"))
    }

    @Test
    fun configure_copiesItemsDefensively() {
        val adapter = PlaylistQuickSelectorAdapter()
        val mutableItems = mutableListOf(file("a.mp4", fsId = 1))
        adapter.configure(mutableItems, null) { }

        // Mutate original list; adapter should be unaffected
        mutableItems.add(file("b.mp4", fsId = 2))

        assertEquals(1, adapter.itemCount)
    }

    private fun createHolder(): PlaylistQuickSelectorAdapter.VH {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(ImageView(context).apply { id = R.id.quickSelectorThumbnail })
        root.addView(TextView(context).apply { id = R.id.quickSelectorFileName })
        root.addView(View(context).apply { id = R.id.quickSelectorFocusBar })
        return PlaylistQuickSelectorAdapter.VH(root)
    }

    private fun file(
        name: String,
        fsId: Long,
        path: String = "/movies/$name",
    ) = FileInfo(
        fsId = fsId,
        path = path,
        serverFilename = name,
        category = 1,
    )
}
