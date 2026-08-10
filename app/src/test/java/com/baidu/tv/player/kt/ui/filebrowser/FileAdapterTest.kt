package com.baidu.tv.player.kt.ui.filebrowser

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemFileBinding
import com.baidu.tv.player.kt.model.FileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileAdapterTest {

    @Test
    fun submitFiles_updatesItemCountAndBindsData() {
        val adapter = FileAdapter()
        val file = file("movie.mp4", category = 1, size = 1024)
        adapter.submitFiles(listOf(file))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, adapter.itemCount)
        assertEquals("movie.mp4", holder.binding.tvFileName.text.toString())
        assertTrue(holder.binding.tvFileInfo.text.toString().isNotBlank())
    }

    @Test
    fun itemClick_invokesCallbackForSinglePlayback() {
        val adapter = FileAdapter()
        val file = file("a.mp4", category = 1)
        var clicked: FileInfo? = null
        adapter.onItemClick = { item, _ -> clicked = item }
        adapter.submitFiles(listOf(file))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()

        assertEquals(file, clicked)
    }

    @Test
    fun multiSelectSelection_rendersCheckedIndicator() {
        val adapter = FileAdapter()
        val file = file("a.jpg", path = "/a.jpg", category = 3)
        adapter.submitFiles(listOf(file))
        adapter.setMultiSelectMode(true)
        adapter.setSelectedPaths(setOf("/a.jpg"))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals(android.view.View.VISIBLE, holder.binding.ivSelected.visibility)
        assertTrue(holder.itemView.isSelected)
        assertEquals(setOf("/a.jpg"), adapter.getSelectedPaths())
    }

    @Test
    fun longClick_invokesCallbackAndReturnsTrue() {
        val adapter = FileAdapter()
        val dir = file("dir", isdir = 1)
        var longClicked: FileInfo? = null
        adapter.onItemLongClick = { item, _ -> longClicked = item; true }
        adapter.submitFiles(listOf(dir))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertTrue(holder.itemView.performLongClick())
        assertEquals(dir, longClicked)
    }

    @Test
    fun multiSelectCanBeDisabled() {
        val adapter = FileAdapter()
        adapter.setMultiSelectMode(true)
        adapter.setMultiSelectMode(false)
        assertFalse(adapter.multiSelectMode)
    }

    private fun createHolder(): FileViewHolder {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = LinearLayout(context).apply {
            isClickable = true
            isFocusable = true
        }
        root.addView(ImageView(context).apply { id = R.id.iv_file_icon })
        root.addView(TextView(context).apply { id = R.id.tv_file_name })
        root.addView(TextView(context).apply { id = R.id.tv_file_info })
        root.addView(ImageView(context).apply { id = R.id.iv_selected })
        return FileViewHolder(ItemFileBinding.bind(root))
    }

    private fun file(
        name: String,
        path: String = "/$name",
        isdir: Int = 0,
        category: Int = 0,
        size: Long = 0,
    ) = FileInfo(path = path, serverFilename = name, isdir = isdir, category = category, size = size)
}
